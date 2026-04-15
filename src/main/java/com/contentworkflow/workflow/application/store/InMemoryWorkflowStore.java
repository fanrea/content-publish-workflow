package com.contentworkflow.workflow.application.store;

import com.contentworkflow.common.api.PageResponse;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.ContentSnapshot;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.entity.ReviewRecord;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.interfaces.dto.DraftQueryRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Demo store: thread-safe collections + local id generators.
 *
 * <p>Important: this is not a database replacement. It exists to keep the workflow layer
 * testable and runnable before persistence is wired in.</p>
 */
@Component
public class InMemoryWorkflowStore implements WorkflowStore {

    private final Map<Long, ContentDraft> drafts = new ConcurrentHashMap<>();
    private final Map<Long, List<ReviewRecord>> reviews = new ConcurrentHashMap<>();
    private final Map<Long, List<ContentSnapshot>> snapshots = new ConcurrentHashMap<>();
    private final Map<Long, List<PublishTask>> tasks = new ConcurrentHashMap<>();
    private final Map<Long, List<PublishLogEntry>> logs = new ConcurrentHashMap<>();
    private final Map<String, PublishCommandEntry> publishCommands = new ConcurrentHashMap<>();

    private final AtomicLong draftIdGenerator = new AtomicLong(1);
    private final AtomicLong reviewIdGenerator = new AtomicLong(1);
    private final AtomicLong snapshotIdGenerator = new AtomicLong(1);
    private final AtomicLong taskIdGenerator = new AtomicLong(1);
    private final AtomicLong logIdGenerator = new AtomicLong(1);
    private final AtomicLong commandIdGenerator = new AtomicLong(1);

    @Override
    public List<ContentDraft> listDrafts() {
        return drafts.values().stream()
                .sorted((left, right) -> right.getUpdatedAt().compareTo(left.getUpdatedAt()))
                .toList();
    }

    @Override
    public PageResponse<ContentDraft> pageDrafts(DraftQueryRequest request) {
        List<ContentDraft> filtered = sortDrafts(filterDrafts(listDrafts(), request), request);

        int pageNo = Objects.requireNonNull(request.pageNo());
        int pageSize = Objects.requireNonNull(request.pageSize());
        long total = filtered.size();
        long totalPages = (total + pageSize - 1) / pageSize;

        int fromIndex = Math.max(0, (pageNo - 1) * pageSize);
        int toIndex = (int) Math.min(total, (long) fromIndex + pageSize);
        List<ContentDraft> pageItems = fromIndex >= toIndex
                ? List.of()
                : filtered.subList(fromIndex, toIndex);
        return new PageResponse<>(pageItems, total, pageNo, pageSize, totalPages);
    }

    @Override
    public Map<WorkflowStatus, Long> countDraftsByStatus(DraftQueryRequest request) {
        return filterDrafts(listDrafts(), request).stream()
                .collect(Collectors.groupingBy(
                        ContentDraft::getStatus,
                        () -> new EnumMap<>(WorkflowStatus.class),
                        Collectors.counting()
                ));
    }

    @Override
    public ContentDraft insertDraft(ContentDraft draft) {
        long id = draftIdGenerator.getAndIncrement();
        draft.setId(id);
        if (draft.getBizNo() == null || draft.getBizNo().isBlank()) {
            draft.setBizNo("CPW-" + id);
        }
        drafts.put(id, draft);
        return draft;
    }

    @Override
    public Optional<ContentDraft> findDraftById(Long draftId) {
        return Optional.ofNullable(drafts.get(draftId));
    }

    @Override
    public ContentDraft updateDraft(ContentDraft draft) {
        drafts.put(draft.getId(), draft);
        return draft;
    }

    @Override
    public ReviewRecord insertReviewRecord(ReviewRecord record) {
        long id = reviewIdGenerator.getAndIncrement();
        record.setId(id);
        reviews.computeIfAbsent(record.getDraftId(), ignored -> new ArrayList<>()).add(record);
        return record;
    }

    @Override
    public List<ReviewRecord> listReviewRecords(Long draftId) {
        return new ArrayList<>(reviews.getOrDefault(draftId, List.of()));
    }

    @Override
    public ContentSnapshot insertSnapshot(ContentSnapshot snapshot) {
        long id = snapshotIdGenerator.getAndIncrement();
        snapshot.setId(id);
        snapshots.computeIfAbsent(snapshot.getDraftId(), ignored -> new ArrayList<>()).add(snapshot);
        return snapshot;
    }

    @Override
    public List<ContentSnapshot> listSnapshots(Long draftId) {
        return new ArrayList<>(snapshots.getOrDefault(draftId, List.of()));
    }

    @Override
    public List<PublishTask> insertPublishTasks(List<PublishTask> taskList) {
        if (taskList == null || taskList.isEmpty()) {
            return List.of();
        }
        Long draftId = taskList.get(0).getDraftId();
        for (PublishTask task : taskList) {
            task.setId(taskIdGenerator.getAndIncrement());
        }
        tasks.computeIfAbsent(draftId, ignored -> new ArrayList<>()).addAll(taskList);
        return taskList;
    }

    @Override
    public List<PublishTask> listPublishTasks(Long draftId) {
        return new ArrayList<>(tasks.getOrDefault(draftId, List.of()));
    }

    @Override
    public PublishTask updatePublishTask(PublishTask task) {
        if (task == null || task.getId() == null) {
            throw new IllegalArgumentException("task/id required");
        }
        Long draftId = task.getDraftId();
        if (draftId == null) {
            // 兜底：全表扫描（仅用于 in-memory demo）。
            draftId = tasks.entrySet().stream()
                    .filter(e -> e.getValue().stream().anyMatch(t -> task.getId().equals(t.getId())))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }
        if (draftId == null) {
            throw new IllegalStateException("task not found: " + task.getId());
        }
        List<PublishTask> list = tasks.getOrDefault(draftId, List.of());
        for (int i = 0; i < list.size(); i++) {
            if (task.getId().equals(list.get(i).getId())) {
                list.set(i, task);
                return task;
            }
        }
        throw new IllegalStateException("task not found: " + task.getId());
    }

    @Override
    public List<PublishTask> listPublishTasksByStatus(PublishTaskStatus status) {
        return tasks.values().stream()
                .flatMap(List::stream)
                .filter(t -> t.getStatus() == status)
                .sorted((l, r) -> l.getUpdatedAt().compareTo(r.getUpdatedAt()))
                .toList();
    }

    @Override
    public List<PublishTask> claimRunnablePublishTasks(int limit, String workerId, LocalDateTime now, int lockSeconds) {
        if (limit <= 0) {
            return List.of();
        }
        LocalDateTime lockExpiredBefore = now.minusSeconds(Math.max(1, lockSeconds));

        // In-memory claim (best-effort): select runnable tasks and mark them RUNNING in place.
        List<PublishTask> candidates = tasks.values().stream()
                .flatMap(List::stream)
                .filter(t -> t.getStatus() == PublishTaskStatus.PENDING || t.getStatus() == PublishTaskStatus.FAILED)
                .filter(t -> t.getNextRunAt() == null || !t.getNextRunAt().isAfter(now))
                .filter(t -> t.getLockedAt() == null || t.getLockedAt().isBefore(lockExpiredBefore))
                .sorted((l, r) -> l.getUpdatedAt().compareTo(r.getUpdatedAt()))
                .limit(limit)
                .toList();

        List<PublishTask> claimed = new ArrayList<>(candidates.size());
        for (PublishTask t : candidates) {
            synchronized (t) {
                if (!(t.getStatus() == PublishTaskStatus.PENDING || t.getStatus() == PublishTaskStatus.FAILED)) {
                    continue;
                }
                if (t.getNextRunAt() != null && t.getNextRunAt().isAfter(now)) {
                    continue;
                }
                if (t.getLockedAt() != null && !t.getLockedAt().isBefore(lockExpiredBefore)) {
                    continue;
                }
                t.setStatus(PublishTaskStatus.RUNNING);
                t.setLockedBy(workerId);
                t.setLockedAt(now);
                t.setUpdatedAt(now);
                claimed.add(t);
            }
        }
        return claimed;
    }

    @Override
    public PublishLogEntry insertPublishLog(PublishLogEntry entry) {
        long id = logIdGenerator.getAndIncrement();
        entry.setId(id);
        logs.computeIfAbsent(entry.getDraftId(), ignored -> new ArrayList<>()).add(entry);
        return entry;
    }

    @Override
    public List<PublishLogEntry> listPublishLogs(Long draftId) {
        return new ArrayList<>(logs.getOrDefault(draftId, List.of()));
    }

    @Override
    public List<PublishLogEntry> listPublishLogsByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        return logs.values().stream()
                .flatMap(List::stream)
                .filter(entry -> Objects.equals(traceId, entry.getTraceId()))
                .sorted(Comparator.comparing(PublishLogEntry::getCreatedAt))
                .toList();
    }

    @Override
    public Optional<PublishCommandEntry> findPublishCommand(Long draftId, String commandType, String idempotencyKey) {
        if (draftId == null || commandType == null || idempotencyKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(publishCommands.get(commandKey(draftId, commandType, idempotencyKey)));
    }

    @Override
    public boolean tryCreatePublishCommand(PublishCommandEntry entry) {
        if (entry == null || entry.getDraftId() == null || entry.getCommandType() == null || entry.getIdempotencyKey() == null) {
            throw new IllegalArgumentException("draftId/commandType/idempotencyKey required");
        }
        String key = commandKey(entry.getDraftId(), entry.getCommandType(), entry.getIdempotencyKey());

        PublishCommandEntry created = PublishCommandEntry.builder()
                .id(commandIdGenerator.getAndIncrement())
                .draftId(entry.getDraftId())
                .commandType(entry.getCommandType())
                .idempotencyKey(entry.getIdempotencyKey())
                .operatorName(entry.getOperatorName())
                .remark(entry.getRemark())
                .status(entry.getStatus())
                .targetPublishedVersion(entry.getTargetPublishedVersion())
                .snapshotId(entry.getSnapshotId())
                .errorMessage(entry.getErrorMessage())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();

        return publishCommands.putIfAbsent(key, created) == null;
    }

    @Override
    public PublishCommandEntry updatePublishCommand(PublishCommandEntry entry) {
        if (entry == null || entry.getDraftId() == null || entry.getCommandType() == null || entry.getIdempotencyKey() == null) {
            throw new IllegalArgumentException("draftId/commandType/idempotencyKey required");
        }
        String key = commandKey(entry.getDraftId(), entry.getCommandType(), entry.getIdempotencyKey());
        PublishCommandEntry existing = publishCommands.get(key);
        if (existing == null) {
            // In-memory demo: if missing, create it to keep the flow going in tests.
            tryCreatePublishCommand(entry);
            return publishCommands.get(key);
        }
        existing.setOperatorName(entry.getOperatorName());
        existing.setRemark(entry.getRemark());
        existing.setStatus(entry.getStatus());
        existing.setTargetPublishedVersion(entry.getTargetPublishedVersion());
        existing.setSnapshotId(entry.getSnapshotId());
        existing.setErrorMessage(entry.getErrorMessage());
        existing.setUpdatedAt(entry.getUpdatedAt());
        return existing;
    }

    @Override
    public List<PublishCommandEntry> listPublishCommands(Long draftId) {
        if (draftId == null) {
            return List.of();
        }
        return publishCommands.values().stream()
                .filter(entry -> Objects.equals(entry.getDraftId(), draftId))
                .sorted(Comparator.comparing(PublishCommandEntry::getCreatedAt).reversed())
                .toList();
    }

    private List<ContentDraft> filterDrafts(List<ContentDraft> source, DraftQueryRequest request) {
        String keyword = request.keyword();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        String keywordLowerCase = hasKeyword ? keyword.toLowerCase() : null;
        boolean searchInBody = Boolean.TRUE.equals(request.searchInBody());
        List<WorkflowStatus> statuses = request.status();

        LocalDateTime createdFrom = request.createdFrom();
        LocalDateTime createdTo = request.createdTo();
        LocalDateTime updatedFrom = request.updatedFrom();
        LocalDateTime updatedTo = request.updatedTo();

        return source.stream()
                .filter(d -> statuses == null || statuses.isEmpty() || statuses.contains(d.getStatus()))
                .filter(d -> {
                    if (!hasKeyword) {
                        return true;
                    }
                    if (containsIgnoreCase(d.getBizNo(), keywordLowerCase)) {
                        return true;
                    }
                    if (containsIgnoreCase(d.getTitle(), keywordLowerCase)) {
                        return true;
                    }
                    if (containsIgnoreCase(d.getSummary(), keywordLowerCase)) {
                        return true;
                    }
                    return searchInBody && containsIgnoreCase(d.getBody(), keywordLowerCase);
                })
                .filter(d -> createdFrom == null || (d.getCreatedAt() != null && !d.getCreatedAt().isBefore(createdFrom)))
                .filter(d -> createdTo == null || (d.getCreatedAt() != null && !d.getCreatedAt().isAfter(createdTo)))
                .filter(d -> updatedFrom == null || (d.getUpdatedAt() != null && !d.getUpdatedAt().isBefore(updatedFrom)))
                .filter(d -> updatedTo == null || (d.getUpdatedAt() != null && !d.getUpdatedAt().isAfter(updatedTo)))
                .toList();
    }

    private List<ContentDraft> sortDrafts(List<ContentDraft> source, DraftQueryRequest request) {
        Comparator<ContentDraft> comparator = switch (request.sortBy()) {
            case ID -> Comparator.comparing(ContentDraft::getId, Comparator.nullsLast(Long::compareTo));
            case CREATED_AT -> Comparator.comparing(ContentDraft::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
            case UPDATED_AT -> Comparator.comparing(ContentDraft::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
        };
        comparator = comparator.thenComparing(ContentDraft::getId, Comparator.nullsLast(Long::compareTo));
        if (request.sortDirection() == DraftQueryRequest.SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        return source.stream().sorted(comparator).toList();
    }

    private boolean containsIgnoreCase(String text, String keywordLowerCase) {
        if (text == null) {
            return false;
        }
        return text.toLowerCase().contains(keywordLowerCase);
    }

    private String commandKey(Long draftId, String commandType, String idempotencyKey) {
        // ASCII-only delimiter to avoid encoding issues on Windows consoles/editors.
        return draftId + "|" + commandType + "|" + idempotencyKey;
    }
}
