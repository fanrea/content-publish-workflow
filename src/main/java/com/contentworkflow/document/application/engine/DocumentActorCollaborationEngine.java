package com.contentworkflow.document.application.engine;

import com.contentworkflow.document.application.ingress.DocumentOperationIngressCommand;
import com.contentworkflow.document.application.realtime.DocumentOperationService;
import com.contentworkflow.document.application.realtime.DocumentRealtimePushService;
import com.contentworkflow.document.domain.entity.DocumentOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Stage1 ingress processing contract:
 * 1) docId is deterministically routed to a single shard;
 * 2) each shard is single-threaded;
 * 3) commands sharing the same docId are processed in submit order on the same shard thread.
 */
@Service
public class DocumentActorCollaborationEngine implements CollaborationEngine, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DocumentActorCollaborationEngine.class);
    private static final int DEFAULT_SHARD_COUNT = Math.max(4, Runtime.getRuntime().availableProcessors());
    private static final int DEFAULT_DOCUMENT_BACKLOG_LIMIT = 512;

    private final DocumentOperationService documentOperationService;
    private final DocumentRealtimePushService pushService;
    private final List<ExecutorService> actorShardExecutors;
    private final List<ExecutorService> durabilityShardExecutors;
    private final List<ExecutorService> pushShardExecutors;
    private final ConcurrentHashMap<Long, AtomicInteger> backlogByDocument;
    private final int documentBacklogLimit;

    public DocumentActorCollaborationEngine(DocumentOperationService documentOperationService,
                                            DocumentRealtimePushService pushService) {
        this(documentOperationService, pushService, DEFAULT_SHARD_COUNT, DEFAULT_DOCUMENT_BACKLOG_LIMIT);
    }

    DocumentActorCollaborationEngine(DocumentOperationService documentOperationService,
                                     DocumentRealtimePushService pushService,
                                     int shardCount) {
        this(documentOperationService, pushService, shardCount, DEFAULT_DOCUMENT_BACKLOG_LIMIT);
    }

    DocumentActorCollaborationEngine(DocumentOperationService documentOperationService,
                                     DocumentRealtimePushService pushService,
                                     int shardCount,
                                     int documentBacklogLimit) {
        if (shardCount <= 0) {
            throw new IllegalArgumentException("shardCount must be > 0");
        }
        if (documentBacklogLimit <= 0) {
            throw new IllegalArgumentException("documentBacklogLimit must be > 0");
        }
        this.documentOperationService = Objects.requireNonNull(documentOperationService, "documentOperationService must not be null");
        this.pushService = Objects.requireNonNull(pushService, "pushService must not be null");
        this.documentBacklogLimit = documentBacklogLimit;
        this.actorShardExecutors = IntStream.range(0, shardCount)
                .mapToObj(this::newSingleThreadShardExecutor)
                .toList();
        this.durabilityShardExecutors = IntStream.range(0, shardCount)
                .mapToObj(this::newSingleThreadDurabilityExecutor)
                .toList();
        this.pushShardExecutors = IntStream.range(0, shardCount)
                .mapToObj(this::newSingleThreadPushExecutor)
                .toList();
        this.backlogByDocument = new ConcurrentHashMap<>();
    }

    @Override
    public void submit(DocumentOperationIngressCommand command) {
        DocumentOperationIngressCommand normalized = Objects.requireNonNull(command, "command must not be null");
        Long documentId = Objects.requireNonNull(normalized.docId(), "documentId must not be null");
        int backlog = incrementDocumentBacklog(documentId);
        if (backlog > documentBacklogLimit) {
            decrementDocumentBacklog(documentId);
            log.warn("collaboration engine backpressure rejected, docId={}, backlog={}, limit={}, clientSeq={}",
                    documentId, backlog, documentBacklogLimit, normalized.clientSeq());
            throw new IllegalStateException("document backlog limit exceeded, docId=" + documentId + ", limit=" + documentBacklogLimit);
        }

        try {
            actorShardExecutor(documentId).execute(() -> dispatchToDurability(normalized));
        } catch (RuntimeException ex) {
            decrementDocumentBacklog(documentId);
            if (ex instanceof RejectedExecutionException) {
                log.warn("collaboration engine actor shard rejected, docId={}, backlog={}, limit={}",
                        documentId, currentBacklog(documentId), documentBacklogLimit, ex);
            }
            throw ex;
        }
    }

    @Override
    public void destroy() {
        shutdownExecutors(actorShardExecutors);
        shutdownExecutors(durabilityShardExecutors);
        shutdownExecutors(pushShardExecutors);
    }

    private void dispatchToDurability(DocumentOperationIngressCommand command) {
        try {
            durabilityShardExecutor(command.docId()).execute(() -> applyAndPush(command));
        } catch (RuntimeException ex) {
            decrementDocumentBacklog(command.docId());
            if (ex instanceof RejectedExecutionException) {
                log.warn("collaboration engine durability shard rejected, docId={}, backlog={}, limit={}",
                        command.docId(), currentBacklog(command.docId()), documentBacklogLimit, ex);
            }
            throw ex;
        }
    }

    private void applyAndPush(DocumentOperationIngressCommand command) {
        try {
            DocumentOperationService.ApplyResult result = documentOperationService.applyOperation(
                    command.docId(),
                    command.baseRevision(),
                    command.sessionId(),
                    command.clientSeq(),
                    command.editorId(),
                    command.editorName(),
                    command.op(),
                    null,
                    null,
                    null,
                    command.deltaBatchId(),
                    command.clientClock(),
                    command.baseVector()
            );
            DocumentOperation operation = result.operation();
            if (!result.duplicated() && operation != null) {
                enqueuePush(command.docId(), operation);
            }
        } catch (Exception ex) {
            log.error("collaboration engine command failed, docId={}, clientSeq={}",
                    command.docId(),
                    command.clientSeq(),
                    ex);
        } finally {
            decrementDocumentBacklog(command.docId());
        }
    }

    private void enqueuePush(Long documentId, DocumentOperation operation) {
        pushShardExecutor(documentId).execute(() -> {
            try {
                pushService.broadcastOperationApplied(operation);
            } catch (Exception ex) {
                log.warn("collaboration engine async push failed, docId={}, operationId={}",
                        documentId,
                        operation.getId(),
                        ex);
            }
        });
    }

    private ExecutorService actorShardExecutor(Long documentId) {
        int shardIndex = resolveShardIndex(documentId, actorShardExecutors.size());
        return actorShardExecutors.get(shardIndex);
    }

    private ExecutorService durabilityShardExecutor(Long documentId) {
        int shardIndex = resolveShardIndex(documentId, durabilityShardExecutors.size());
        return durabilityShardExecutors.get(shardIndex);
    }

    private ExecutorService pushShardExecutor(Long documentId) {
        int shardIndex = resolveShardIndex(documentId, pushShardExecutors.size());
        return pushShardExecutors.get(shardIndex);
    }

    static int resolveShardIndex(Long documentId, int shardCount) {
        Objects.requireNonNull(documentId, "documentId must not be null");
        if (shardCount <= 0) {
            throw new IllegalArgumentException("shardCount must be > 0");
        }
        return Math.floorMod(documentId.hashCode(), shardCount);
    }

    private ExecutorService newSingleThreadShardExecutor(int shardId) {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "doc-actor-shard-" + shardId);
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadExecutor(threadFactory);
    }

    private ExecutorService newSingleThreadDurabilityExecutor(int shardId) {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "doc-durability-shard-" + shardId);
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadExecutor(threadFactory);
    }

    private ExecutorService newSingleThreadPushExecutor(int shardId) {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "doc-push-shard-" + shardId);
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadExecutor(threadFactory);
    }

    private void shutdownExecutors(List<ExecutorService> executors) {
        for (ExecutorService executor : executors) {
            executor.shutdown();
        }
        for (ExecutorService executor : executors) {
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    int currentBacklog(Long documentId) {
        AtomicInteger counter = backlogByDocument.get(documentId);
        return counter == null ? 0 : Math.max(counter.get(), 0);
    }

    Map<Long, Integer> backlogSnapshot() {
        ConcurrentHashMap<Long, Integer> snapshot = new ConcurrentHashMap<>();
        backlogByDocument.forEach((docId, counter) -> {
            int value = counter.get();
            if (value > 0) {
                snapshot.put(docId, value);
            }
        });
        return snapshot;
    }

    private int incrementDocumentBacklog(Long documentId) {
        AtomicInteger counter = backlogByDocument.computeIfAbsent(documentId, ignored -> new AtomicInteger());
        return counter.incrementAndGet();
    }

    private void decrementDocumentBacklog(Long documentId) {
        backlogByDocument.computeIfPresent(documentId, (ignored, counter) -> {
            int remaining = counter.decrementAndGet();
            return remaining <= 0 ? null : counter;
        });
    }
}
