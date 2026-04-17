package com.contentworkflow.workflow.application.store;

import com.contentworkflow.common.api.PageResponse;
import com.contentworkflow.workflow.domain.entity.ContentDraft;
import com.contentworkflow.workflow.domain.entity.ContentSnapshot;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.entity.ReviewRecord;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.interfaces.dto.DraftQueryRequest;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 存储抽象，用于统一封装业务对象的持久化读写与查询访问。
 */
public interface WorkflowStore {

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @return 符合条件的结果集合
     */

    List<ContentDraft> listDrafts();

    /**
     * 按分页条件查询数据，并返回包含分页元信息的结果。
     *
     * @param request 封装业务输入的请求对象
     * @return 包含分页数据和分页元信息的结果对象
     */

    PageResponse<ContentDraft> pageDrafts(DraftQueryRequest request);

    /**
     * 统计满足条件的数据数量，用于计数或监控场景。
     *
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

    Map<WorkflowStatus, Long> countDraftsByStatus(DraftQueryRequest request);

    /**
     * 处理 insert draft 相关逻辑，并返回对应的执行结果。
     *
     * @param draft 草稿对象
     * @return 方法处理后的结果对象
     */

    ContentDraft insertDraft(ContentDraft draft);

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param draftId 草稿唯一标识
     * @return 匹配到结果时返回对应对象，否则返回空的 Optional 容器
     */

    Optional<ContentDraft> findDraftById(Long draftId);

    Optional<DraftOperationLockEntry> findDraftOperationLock(Long draftId);

    boolean tryAcquireDraftOperationLock(DraftOperationLockEntry lockEntry, LocalDateTime now);

    boolean renewDraftOperationLock(Long draftId,
                                    Integer targetPublishedVersion,
                                    String lockedBy,
                                    LocalDateTime lockedAt,
                                    LocalDateTime expiresAt);

    boolean releaseDraftOperationLock(Long draftId, Integer targetPublishedVersion);

    /**
     * 根据输入参数更新已有业务对象，并返回更新后的状态。
     *
     * @param draft 草稿对象
     * @return 方法处理后的结果对象
     */

    default ContentDraft updateDraft(ContentDraft draft) {
        return updateDraft(draft, EnumSet.allOf(WorkflowStatus.class));
    }

    ContentDraft updateDraft(ContentDraft draft, EnumSet<WorkflowStatus> expectedStatuses);

    /**
     * 处理 insert review record 相关逻辑，并返回对应的执行结果。
     *
     * @param record 记录对象
     * @return 方法处理后的结果对象
     */

    ReviewRecord insertReviewRecord(ReviewRecord record);

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    List<ReviewRecord> listReviewRecords(Long draftId);

    /**
     * 处理 insert snapshot 相关逻辑，并返回对应的执行结果。
     *
     * @param snapshot 参数 snapshot 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    ContentSnapshot insertSnapshot(ContentSnapshot snapshot);

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    List<ContentSnapshot> listSnapshots(Long draftId);

    /**
     * 处理 insert publish tasks 相关逻辑，并返回对应的执行结果。
     *
     * @param tasks 参数 tasks 对应的业务输入值
     * @return 符合条件的结果集合
     */

    List<PublishTask> insertPublishTasks(List<PublishTask> tasks);

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    List<PublishTask> listPublishTasks(Long draftId);

    /**
     * 根据输入参数更新已有业务对象，并返回更新后的状态。
     *
     * @param task 任务对象
     * @return 方法处理后的结果对象
     */

    PublishTask updatePublishTask(PublishTask task);

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param status 状态值
     * @return 符合条件的结果集合
     */

    List<PublishTask> listPublishTasksByStatus(PublishTaskStatus status);

    /**
     * 处理 claim runnable publish tasks 相关逻辑，并返回对应的执行结果。
     *
     * @param limit 参数 limit 对应的业务输入值
     * @param workerId 相关业务对象的唯一标识
     * @param now 参数 now 对应的业务输入值
     * @param lockSeconds 参数 lockSeconds 对应的业务输入值
     * @return 符合条件的结果集合
     */

    List<PublishTask> claimRunnablePublishTasks(int limit, String workerId, LocalDateTime now, int lockSeconds);

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param draftId 草稿唯一标识
     * @param commandType 参数 commandType 对应的业务输入值
     * @param idempotencyKey 参数 idempotencyKey 对应的业务输入值
     * @return 匹配到结果时返回对应对象，否则返回空的 Optional 容器
     */

    Optional<PublishCommandEntry> findPublishCommand(Long draftId, String commandType, String idempotencyKey);

    /**
     * 处理 try create publish command 相关逻辑，并返回对应的执行结果。
     *
     * @param entry 参数 entry 对应的业务输入值
     * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
     */

    boolean tryCreatePublishCommand(PublishCommandEntry entry);

    /**
     * 根据输入参数更新已有业务对象，并返回更新后的状态。
     *
     * @param entry 参数 entry 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    PublishCommandEntry updatePublishCommand(PublishCommandEntry entry);

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    List<PublishCommandEntry> listPublishCommands(Long draftId);

    /**
     * 处理 insert publish log 相关逻辑，并返回对应的执行结果。
     *
     * @param entry 参数 entry 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    PublishLogEntry insertPublishLog(PublishLogEntry entry);

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    List<PublishLogEntry> listPublishLogs(Long draftId);

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param traceId 链路追踪标识
     * @return 符合条件的结果集合
     */

    List<PublishLogEntry> listPublishLogsByTraceId(String traceId);
}
