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
 * 存储抽象，用于统一封装业务对象的持久化读写与查询访问。
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

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @return 符合条件的结果集合
     */

    @Override
    public List<ContentDraft> listDrafts() {
        return drafts.values().stream()
                .sorted((left, right) -> right.getUpdatedAt().compareTo(left.getUpdatedAt()))
                .toList();
    }

    /**
     * 按分页条件查询数据，并返回包含分页元信息的结果。
     *
     * @param request 封装业务输入的请求对象
     * @return 包含分页数据和分页元信息的结果对象
     */

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

    /**
     * 统计满足条件的数据数量，用于计数或监控场景。
     *
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    @Override
    public Map<WorkflowStatus, Long> countDraftsByStatus(DraftQueryRequest request) {
        return filterDrafts(listDrafts(), request).stream()
                .collect(Collectors.groupingBy(
                        ContentDraft::getStatus,
                        () -> new EnumMap<>(WorkflowStatus.class),
                        Collectors.counting()
                ));
    }

    /**
     * 处理 insert draft 相关逻辑，并返回对应的执行结果。
     *
     * @param draft 草稿对象
     * @return 方法处理后的结果对象
     */

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

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param draftId 草稿唯一标识
     * @return 匹配到结果时返回对应对象，否则返回空的 Optional 容器
     */

    @Override
    public Optional<ContentDraft> findDraftById(Long draftId) {
        return Optional.ofNullable(drafts.get(draftId));
    }

    /**
     * 根据输入参数更新已有业务对象，并返回更新后的状态。
     *
     * @param draft 草稿对象
     * @return 方法处理后的结果对象
     */

    @Override
    public ContentDraft updateDraft(ContentDraft draft) {
        drafts.put(draft.getId(), draft);
        return draft;
    }

    /**
     * 处理 insert review record 相关逻辑，并返回对应的执行结果。
     *
     * @param record 记录对象
     * @return 方法处理后的结果对象
     */

    @Override
    public ReviewRecord insertReviewRecord(ReviewRecord record) {
        long id = reviewIdGenerator.getAndIncrement();
        record.setId(id);
        reviews.computeIfAbsent(record.getDraftId(), ignored -> new ArrayList<>()).add(record);
        return record;
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    @Override
    public List<ReviewRecord> listReviewRecords(Long draftId) {
        return new ArrayList<>(reviews.getOrDefault(draftId, List.of()));
    }

    /**
     * 处理 insert snapshot 相关逻辑，并返回对应的执行结果。
     *
     * @param snapshot 参数 snapshot 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    @Override
    public ContentSnapshot insertSnapshot(ContentSnapshot snapshot) {
        long id = snapshotIdGenerator.getAndIncrement();
        snapshot.setId(id);
        snapshots.computeIfAbsent(snapshot.getDraftId(), ignored -> new ArrayList<>()).add(snapshot);
        return snapshot;
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    @Override
    public List<ContentSnapshot> listSnapshots(Long draftId) {
        return new ArrayList<>(snapshots.getOrDefault(draftId, List.of()));
    }

    /**
     * 处理 insert publish tasks 相关逻辑，并返回对应的执行结果。
     *
     * @param taskList 待处理的数据集合
     * @return 符合条件的结果集合
     */

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

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    @Override
    public List<PublishTask> listPublishTasks(Long draftId) {
        return new ArrayList<>(tasks.getOrDefault(draftId, List.of()));
    }

    /**
     * 根据输入参数更新已有业务对象，并返回更新后的状态。
     *
     * @param task 任务对象
     * @return 方法处理后的结果对象
     */

    @Override
    public PublishTask updatePublishTask(PublishTask task) {
        if (task == null || task.getId() == null) {
            throw new IllegalArgumentException("task/id required");
        }
        Long draftId = task.getDraftId();
        if (draftId == null) {
            // Fallback to a full scan in the in-memory demo store when draftId is absent.
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

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param status 状态值
     * @return 符合条件的结果集合
     */

    @Override
    public List<PublishTask> listPublishTasksByStatus(PublishTaskStatus status) {
        return tasks.values().stream()
                .flatMap(List::stream)
                .filter(t -> t.getStatus() == status)
                .sorted((l, r) -> l.getUpdatedAt().compareTo(r.getUpdatedAt()))
                .toList();
    }

    /**
     * 处理 claim runnable publish tasks 相关逻辑，并返回对应的执行结果。
     *
     * @param limit 参数 limit 对应的业务输入值
     * @param workerId 相关业务对象的唯一标识
     * @param now 参数 now 对应的业务输入值
     * @param lockSeconds 参数 lockSeconds 对应的业务输入值
     * @return 符合条件的结果集合
     */

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

    /**
     * 处理 insert publish log 相关逻辑，并返回对应的执行结果。
     *
     * @param entry 参数 entry 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    @Override
    public PublishLogEntry insertPublishLog(PublishLogEntry entry) {
        long id = logIdGenerator.getAndIncrement();
        entry.setId(id);
        logs.computeIfAbsent(entry.getDraftId(), ignored -> new ArrayList<>()).add(entry);
        return entry;
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    @Override
    public List<PublishLogEntry> listPublishLogs(Long draftId) {
        return new ArrayList<>(logs.getOrDefault(draftId, List.of()));
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param traceId 链路追踪标识
     * @return 符合条件的结果集合
     */

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

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param draftId 草稿唯一标识
     * @param commandType 参数 commandType 对应的业务输入值
     * @param idempotencyKey 参数 idempotencyKey 对应的业务输入值
     * @return 匹配到结果时返回对应对象，否则返回空的 Optional 容器
     */

    @Override
    public Optional<PublishCommandEntry> findPublishCommand(Long draftId, String commandType, String idempotencyKey) {
        if (draftId == null || commandType == null || idempotencyKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(publishCommands.get(commandKey(draftId, commandType, idempotencyKey)));
    }

    /**
     * 处理 try create publish command 相关逻辑，并返回对应的执行结果。
     *
     * @param entry 参数 entry 对应的业务输入值
     * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
     */

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

    /**
     * 根据输入参数更新已有业务对象，并返回更新后的状态。
     *
     * @param entry 参数 entry 对应的业务输入值
     * @return 方法处理后的结果对象
     */

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

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

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

    /**
     * 处理 filter drafts 相关逻辑，并返回对应的执行结果。
     *
     * @param source 参数 source 对应的业务输入值
     * @param request 封装业务输入的请求对象
     * @return 符合条件的结果集合
     */

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

    /**
     * 处理 sort drafts 相关逻辑，并返回对应的执行结果。
     *
     * @param source 参数 source 对应的业务输入值
     * @param request 封装业务输入的请求对象
     * @return 符合条件的结果集合
     */

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

    /**
     * 处理 contains ignore case 相关逻辑，并返回对应的执行结果。
     *
     * @param text 参数 text 对应的业务输入值
     * @param keywordLowerCase 参数 keywordLowerCase 对应的业务输入值
     * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
     */

    private boolean containsIgnoreCase(String text, String keywordLowerCase) {
        if (text == null) {
            return false;
        }
        return text.toLowerCase().contains(keywordLowerCase);
    }

    /**
     * 处理 command key 相关逻辑，并返回对应的执行结果。
     *
     * @param draftId 草稿唯一标识
     * @param commandType 参数 commandType 对应的业务输入值
     * @param idempotencyKey 参数 idempotencyKey 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private String commandKey(Long draftId, String commandType, String idempotencyKey) {
        // ASCII-only delimiter to avoid encoding issues on Windows consoles/editors.
        return draftId + "|" + commandType + "|" + idempotencyKey;
    }
}
