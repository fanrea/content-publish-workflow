package com.contentworkflow.workflow.infrastructure.persistence;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.workflow.application.store.DraftOperationLockEntry;
import com.contentworkflow.workflow.application.store.JpaWorkflowStore;
import com.contentworkflow.workflow.domain.enums.DraftOperationType;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import com.contentworkflow.workflow.domain.enums.ReviewDecision;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.infrastructure.persistence.entity.*;
import com.contentworkflow.workflow.infrastructure.persistence.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

import static com.contentworkflow.testing.BusinessExceptionAssertions.assertCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 测试类，用于验证当前模块在特定场景下的行为、状态变化或边界条件。
 */

@DataJpaTest
class PersistenceLayerTest {

    @Autowired
    private ContentDraftJpaRepository draftRepo;

    @Autowired
    private ReviewRecordJpaRepository reviewRepo;

    @Autowired
    private ContentSnapshotJpaRepository snapshotRepo;

    @Autowired
    private PublishTaskJpaRepository taskRepo;

    @Autowired
    private PublishLogJpaRepository logRepo;

    @Autowired
    private PublishCommandJpaRepository commandRepo;

    @Autowired
    private DraftOperationLockJpaRepository operationLockRepo;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    /**
     * 处理 repositories_can persist and query 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void repositories_canPersistAndQuery() {
        ContentDraftJpaEntity draft = new ContentDraftJpaEntity();
        draft.setBizNo("BIZ-001");
        draft.setTitle("t");
        draft.setSummary("s");
        draft.setBody("b");
        draft.setDraftVersion(1);
        draft.setPublishedVersion(0);
        draft.setWorkflowStatus(WorkflowStatus.DRAFT);
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        draft = draftRepo.saveAndFlush(draft);

        assertThat(draft.getId()).isNotNull();
        assertThat(draftRepo.findByBizNo("BIZ-001")).isPresent();
        assertThat(draftRepo.countByWorkflowStatus(WorkflowStatus.DRAFT)).isGreaterThanOrEqualTo(1);

        ReviewRecordJpaEntity review = new ReviewRecordJpaEntity();
        review.setDraftId(draft.getId());
        review.setDraftVersion(1);
        review.setReviewer("op");
        review.setDecision(ReviewDecision.APPROVE);
        review.setComment("ok");
        review.setReviewedAt(LocalDateTime.now());
        review = reviewRepo.saveAndFlush(review);
        assertThat(review.getId()).isNotNull();
        assertThat(reviewRepo.findTop1ByDraftIdOrderByReviewedAtDesc(draft.getId())).isPresent();

        ContentSnapshotJpaEntity snapshot = new ContentSnapshotJpaEntity();
        snapshot.setDraftId(draft.getId());
        snapshot.setPublishedVersion(1);
        snapshot.setSourceDraftVersion(1);
        snapshot.setTitle(draft.getTitle());
        snapshot.setSummary(draft.getSummary());
        snapshot.setBody(draft.getBody());
        snapshot.setOperatorName("op");
        snapshot.setRollback(false);
        snapshot.setPublishedAt(LocalDateTime.now());
        snapshot = snapshotRepo.saveAndFlush(snapshot);
        assertThat(snapshot.getId()).isNotNull();
        assertThat(snapshotRepo.findByDraftIdAndPublishedVersion(draft.getId(), 1)).isPresent();

        PublishTaskJpaEntity task = new PublishTaskJpaEntity();
        task.setDraftId(draft.getId());
        task.setPublishedVersion(1);
        task.setTaskType(PublishTaskType.REFRESH_SEARCH_INDEX);
        task.setStatus(PublishTaskStatus.PENDING);
        task.setRetryTimes(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task = taskRepo.saveAndFlush(task);
        assertThat(task.getId()).isNotNull();
        assertThat(taskRepo.findByStatusOrderByUpdatedAtAsc(PublishTaskStatus.PENDING)).isNotEmpty();

        PublishCommandJpaEntity command = new PublishCommandJpaEntity();
        command.setDraftId(draft.getId());
        command.setCommandType("PUBLISH");
        command.setIdempotencyKey("cmd-001");
        command.setOperatorName("op");
        command.setRemark("idempotent publish");
        command.setCommandStatus("ACCEPTED");
        command.setTargetPublishedVersion(1);
        command.setSnapshotId(snapshot.getId());
        command.setCreatedAt(LocalDateTime.now());
        command.setUpdatedAt(LocalDateTime.now());
        command = commandRepo.saveAndFlush(command);
        assertThat(command.getId()).isNotNull();
        assertThat(commandRepo.findByDraftIdAndCommandTypeAndIdempotencyKey(draft.getId(), "PUBLISH", "cmd-001")).isPresent();
        assertThat(commandRepo.findByDraftIdOrderByCreatedAtDesc(draft.getId())).isNotEmpty();

        PublishLogJpaEntity log = new PublishLogJpaEntity();
        log.setDraftId(draft.getId());
        log.setActionType("PUBLISH");
        log.setOperatorName("op");
        log.setRemark("r");
        log.setCreatedAt(LocalDateTime.now());
        log = logRepo.saveAndFlush(log);
        assertThat(log.getId()).isNotNull();
        assertThat(logRepo.findByDraftIdOrderByCreatedAtDesc(draft.getId())).isNotEmpty();
    }

    @Test
    void workflowStore_shouldRejectStaleDraftVersion() {
        JpaWorkflowStore store = new JpaWorkflowStore(
                draftRepo,
                reviewRepo,
                snapshotRepo,
                taskRepo,
                logRepo,
                commandRepo,
                operationLockRepo,
                entityManager
        );

        ContentDraftJpaEntity draft = new ContentDraftJpaEntity();
        draft.setBizNo("BIZ-CONCURRENT-001");
        draft.setTitle("t1");
        draft.setSummary("s1");
        draft.setBody("b1");
        draft.setDraftVersion(1);
        draft.setPublishedVersion(0);
        draft.setWorkflowStatus(WorkflowStatus.DRAFT);
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        draft = draftRepo.saveAndFlush(draft);

        com.contentworkflow.workflow.domain.entity.ContentDraft stale = store.findDraftById(draft.getId()).orElseThrow();

        draft.setTitle("t2");
        draft.setUpdatedAt(LocalDateTime.now());
        draftRepo.saveAndFlush(draft);

        stale.setTitle("t3");
        stale.setUpdatedAt(LocalDateTime.now());
        BusinessException ex = assertThrows(BusinessException.class, () -> store.updateDraft(stale));
        assertCode(ex, "CONCURRENT_MODIFICATION");
    }

    @Test
    void workflowStore_shouldRejectUnexpectedDraftStateForConditionalUpdate() {
        JpaWorkflowStore store = new JpaWorkflowStore(
                draftRepo,
                reviewRepo,
                snapshotRepo,
                taskRepo,
                logRepo,
                commandRepo,
                operationLockRepo,
                entityManager
        );

        ContentDraftJpaEntity draft = new ContentDraftJpaEntity();
        draft.setBizNo("BIZ-STATE-001");
        draft.setTitle("t1");
        draft.setSummary("s1");
        draft.setBody("b1");
        draft.setDraftVersion(1);
        draft.setPublishedVersion(0);
        draft.setWorkflowStatus(WorkflowStatus.DRAFT);
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        draft = draftRepo.saveAndFlush(draft);

        int transitioned = draftRepo.conditionalUpdate(
                draft.getId(),
                draft.getVersion(),
                EnumSet.of(WorkflowStatus.DRAFT),
                draft.getBizNo(),
                draft.getTitle(),
                draft.getSummary(),
                draft.getBody(),
                draft.getDraftVersion(),
                draft.getPublishedVersion(),
                WorkflowStatus.REVIEWING,
                draft.getCurrentSnapshotId(),
                draft.getLastReviewComment(),
                LocalDateTime.now()
        );
        assertThat(transitioned).isEqualTo(1);

        com.contentworkflow.workflow.domain.entity.ContentDraft reviewing = store.findDraftById(draft.getId()).orElseThrow();
        reviewing.setTitle("t2");
        reviewing.setUpdatedAt(LocalDateTime.now());

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> store.updateDraft(reviewing, EnumSet.of(WorkflowStatus.DRAFT))
        );
        assertCode(ex, "INVALID_WORKFLOW_STATE");
    }

    @Test
    void workflowStore_shouldAcquireAndReleaseDraftOperationLock() {
        JpaWorkflowStore store = new JpaWorkflowStore(
                draftRepo,
                reviewRepo,
                snapshotRepo,
                taskRepo,
                logRepo,
                commandRepo,
                operationLockRepo,
                entityManager
        );

        ContentDraftJpaEntity draft = new ContentDraftJpaEntity();
        draft.setBizNo("BIZ-LOCK-001");
        draft.setTitle("t1");
        draft.setSummary("s1");
        draft.setBody("b1");
        draft.setDraftVersion(1);
        draft.setPublishedVersion(0);
        draft.setWorkflowStatus(WorkflowStatus.DRAFT);
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        draft = draftRepo.saveAndFlush(draft);

        LocalDateTime now = LocalDateTime.now();
        DraftOperationLockEntry lock = DraftOperationLockEntry.builder()
                .draftId(draft.getId())
                .operationType(DraftOperationType.PUBLISH)
                .targetPublishedVersion(1)
                .lockedBy("test")
                .lockedAt(now)
                .expiresAt(now.plusMinutes(10))
                .build();

        assertThat(store.tryAcquireDraftOperationLock(lock, now)).isTrue();
        assertThat(store.findDraftOperationLock(draft.getId())).isPresent();
        LocalDateTime renewedAt = now.plusMinutes(1);
        LocalDateTime renewedExpiresAt = renewedAt.plusMinutes(10);
        assertThat(store.renewDraftOperationLock(draft.getId(), 1, "worker-1", renewedAt, renewedExpiresAt)).isTrue();
        DraftOperationLockEntry renewed = store.findDraftOperationLock(draft.getId()).orElseThrow();
        assertThat(renewed.getLockedBy()).isEqualTo("worker-1");
        assertThat(renewed.getLockedAt()).isEqualTo(renewedAt.truncatedTo(ChronoUnit.MICROS));
        assertThat(renewed.getExpiresAt()).isEqualTo(renewedExpiresAt.truncatedTo(ChronoUnit.MICROS));
        assertThat(store.releaseDraftOperationLock(draft.getId(), 1)).isTrue();
        assertThat(store.findDraftOperationLock(draft.getId())).isEmpty();
    }
}
