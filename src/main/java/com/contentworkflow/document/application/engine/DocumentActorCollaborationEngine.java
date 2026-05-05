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
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
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

    private final DocumentOperationService documentOperationService;
    private final DocumentRealtimePushService pushService;
    private final List<ExecutorService> shardExecutors;
    private final List<ExecutorService> pushShardExecutors;

    public DocumentActorCollaborationEngine(DocumentOperationService documentOperationService,
                                            DocumentRealtimePushService pushService) {
        this(documentOperationService, pushService, DEFAULT_SHARD_COUNT);
    }

    DocumentActorCollaborationEngine(DocumentOperationService documentOperationService,
                                     DocumentRealtimePushService pushService,
                                     int shardCount) {
        if (shardCount <= 0) {
            throw new IllegalArgumentException("shardCount must be > 0");
        }
        this.documentOperationService = Objects.requireNonNull(documentOperationService, "documentOperationService must not be null");
        this.pushService = Objects.requireNonNull(pushService, "pushService must not be null");
        this.shardExecutors = IntStream.range(0, shardCount)
                .mapToObj(this::newSingleThreadShardExecutor)
                .toList();
        this.pushShardExecutors = IntStream.range(0, shardCount)
                .mapToObj(this::newSingleThreadPushExecutor)
                .toList();
    }

    @Override
    public void submit(DocumentOperationIngressCommand command) {
        DocumentOperationIngressCommand normalized = Objects.requireNonNull(command, "command must not be null");
        Long documentId = Objects.requireNonNull(normalized.docId(), "documentId must not be null");
        shardExecutor(documentId).execute(() -> applyAndPush(normalized));
    }

    @Override
    public void destroy() {
        shutdownExecutors(shardExecutors);
        shutdownExecutors(pushShardExecutors);
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

    private ExecutorService shardExecutor(Long documentId) {
        int shardIndex = resolveShardIndex(documentId, shardExecutors.size());
        return shardExecutors.get(shardIndex);
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
}
