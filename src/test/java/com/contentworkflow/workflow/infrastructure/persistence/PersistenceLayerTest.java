package com.contentworkflow.workflow.infrastructure.persistence;

import com.contentworkflow.common.messaging.outbox.MybatisOutboxEventRepository;
import com.contentworkflow.common.messaging.outbox.OutboxEventEntity;
import com.contentworkflow.common.messaging.outbox.OutboxEventStatus;
import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.workflow.application.store.DraftOperationLockEntry;
import com.contentworkflow.workflow.application.store.MybatisWorkflowStore;
import com.contentworkflow.workflow.domain.enums.DraftOperationType;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;
import com.contentworkflow.workflow.domain.enums.ReviewDecision;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentDraftEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentSnapshotEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishCommandEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishLogEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishTaskEntity;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ReviewRecordEntity;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.ContentDraftMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.ContentSnapshotMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.DraftOperationLockMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.PublishCommandMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.PublishLogMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.PublishTaskMybatisMapper;
import com.contentworkflow.workflow.infrastructure.persistence.mybatis.ReviewRecordMybatisMapper;
import com.contentworkflow.workflow.interfaces.dto.DraftQueryRequest;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;

import static com.contentworkflow.testing.BusinessExceptionAssertions.assertCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MybatisTest
@Import({MybatisWorkflowStore.class, MybatisOutboxEventRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class PersistenceLayerTest {

    @Autowired
    private ContentDraftMybatisMapper draftMapper;

    @Autowired
    private ReviewRecordMybatisMapper reviewMapper;

    @Autowired
    private ContentSnapshotMybatisMapper snapshotMapper;

    @Autowired
    private PublishTaskMybatisMapper taskMapper;

    @Autowired
    private PublishLogMybatisMapper logMapper;

    @Autowired
    private PublishCommandMybatisMapper commandMapper;

    @Autowired
    private DraftOperationLockMybatisMapper operationLockMapper;

    @Autowired
    private MybatisWorkflowStore store;

    @Autowired
    private MybatisOutboxEventRepository outboxRepository;

    @Test
    void mappers_canPersistAndQuery() {
        ContentDraftEntity draft = new ContentDraftEntity();
        draft.setBizNo("BIZ-001");
        draft.setTitle("t");
        draft.setSummary("s");
        draft.setBody("b");
        draft.setDraftVersion(1);
        draft.setPublishedVersion(0);
        draft.setWorkflowStatus(WorkflowStatus.DRAFT);
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        draft.prepareForInsert();
        draftMapper.insert(draft);

        assertThat(draft.getId()).isNotNull();
        assertThat(draftMapper.selectByBizNo("BIZ-001")).isPresent();
        assertThat(store.countDraftsByStatus(new DraftQueryRequest(null, null, false, 1, 20,
                DraftQueryRequest.DraftSortBy.UPDATED_AT, DraftQueryRequest.SortDirection.DESC,
                null, null, null, null))).containsEntry(WorkflowStatus.DRAFT, 1L);

        ReviewRecordEntity review = new ReviewRecordEntity();
        review.setDraftId(draft.getId());
        review.setDraftVersion(1);
        review.setReviewer("op");
        review.setDecision(ReviewDecision.APPROVE);
        review.setComment("ok");
        review.setReviewedAt(LocalDateTime.now());
        review.prepareForInsert();
        reviewMapper.insert(review);
        assertThat(review.getId()).isNotNull();
        assertThat(reviewMapper.selectLatestByDraftId(draft.getId())).isPresent();

        ContentSnapshotEntity snapshot = new ContentSnapshotEntity();
        snapshot.setDraftId(draft.getId());
        snapshot.setPublishedVersion(1);
        snapshot.setSourceDraftVersion(1);
        snapshot.setTitle(draft.getTitle());
        snapshot.setSummary(draft.getSummary());
        snapshot.setBody(draft.getBody());
        snapshot.setOperatorName("op");
        snapshot.setRollback(false);
        snapshot.setPublishedAt(LocalDateTime.now());
        snapshot.prepareForInsert();
        snapshotMapper.insert(snapshot);
        assertThat(snapshot.getId()).isNotNull();
        assertThat(snapshotMapper.selectByDraftIdAndPublishedVersion(draft.getId(), 1)).isPresent();

        PublishTaskEntity task = new PublishTaskEntity();
        task.setDraftId(draft.getId());
        task.setPublishedVersion(1);
        task.setTaskType(PublishTaskType.REFRESH_SEARCH_INDEX);
        task.setStatus(PublishTaskStatus.PENDING);
        task.setRetryTimes(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.prepareForInsert();
        taskMapper.insert(task);
        assertThat(task.getId()).isNotNull();
        assertThat(taskMapper.selectByStatusOrderByUpdatedAtAsc(PublishTaskStatus.PENDING)).isNotEmpty();

        PublishCommandEntity command = new PublishCommandEntity();
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
        command.prepareForInsert();
        commandMapper.insert(command);
        assertThat(command.getId()).isNotNull();
        assertThat(commandMapper.selectByUniqueKey(draft.getId(), "PUBLISH", "cmd-001")).isPresent();
        assertThat(commandMapper.selectByDraftIdOrderByCreatedAtDesc(draft.getId())).isNotEmpty();

        PublishLogEntity log = new PublishLogEntity();
        log.setDraftId(draft.getId());
        log.setActionType("PUBLISH");
        log.setOperatorName("op");
        log.setRemark("r");
        log.setCreatedAt(LocalDateTime.now());
        log.prepareForInsert();
        logMapper.insert(log);
        assertThat(log.getId()).isNotNull();
        assertThat(logMapper.selectByDraftIdOrderByCreatedAtDesc(draft.getId())).isNotEmpty();
    }

    @Test
    void workflowStore_shouldRejectStaleDraftVersion() {
        ContentDraftEntity draft = createDraft("BIZ-CONCURRENT-001");
        com.contentworkflow.workflow.domain.entity.ContentDraft stale = store.findDraftById(draft.getId()).orElseThrow();

        draft.setTitle("t2");
        draft.setUpdatedAt(LocalDateTime.now());
        draftMapper.conditionalUpdate(
                draft.getId(),
                draft.getVersion(),
                EnumSet.of(WorkflowStatus.DRAFT),
                draft.getBizNo(),
                draft.getTitle(),
                draft.getSummary(),
                draft.getBody(),
                draft.getDraftVersion(),
                draft.getPublishedVersion(),
                draft.getWorkflowStatus(),
                draft.getCurrentSnapshotId(),
                draft.getLastReviewComment(),
                draft.getUpdatedAt()
        );

        stale.setTitle("t3");
        stale.setUpdatedAt(LocalDateTime.now());
        BusinessException ex = assertThrows(BusinessException.class, () -> store.updateDraft(stale));
        assertCode(ex, "CONCURRENT_MODIFICATION");
    }

    @Test
    void workflowStore_shouldRejectUnexpectedDraftStateForConditionalUpdate() {
        ContentDraftEntity draft = createDraft("BIZ-STATE-001");

        int transitioned = draftMapper.conditionalUpdate(
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
        ContentDraftEntity draft = createDraft("BIZ-LOCK-001");

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
        assertThat(Math.abs(ChronoUnit.MICROS.between(renewedAt, renewed.getLockedAt()))).isLessThanOrEqualTo(1);
        assertThat(Math.abs(ChronoUnit.MICROS.between(renewedExpiresAt, renewed.getExpiresAt()))).isLessThanOrEqualTo(1);
        assertThat(store.releaseDraftOperationLock(draft.getId(), 1)).isTrue();
        assertThat(operationLockMapper.selectByDraftId(draft.getId())).isEmpty();
    }

    @Test
    void outboxRepository_shouldHonorRequestedSortForStatusQueries() {
        LocalDateTime baseTime = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        OutboxEventEntity earlier = createOutboxEvent("evt-status-1", "article", "A-1", baseTime);
        OutboxEventEntity later = createOutboxEvent("evt-status-2", "article", "A-2", baseTime.plusMinutes(10));

        List<OutboxEventEntity> ascending = outboxRepository.findByStatusIn(
                EnumSet.of(OutboxEventStatus.FAILED),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt"))
        );

        assertThat(ascending)
                .extracting(OutboxEventEntity::getId)
                .containsExactly(earlier.getId(), later.getId());

        List<OutboxEventEntity> descending = outboxRepository.findByStatusIn(
                EnumSet.of(OutboxEventStatus.FAILED),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(descending)
                .extracting(OutboxEventEntity::getId)
                .containsExactly(later.getId(), earlier.getId());
    }

    @Test
    void outboxRepository_shouldHonorRequestedSortForAggregateQueries() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 2, 9, 0, 0);
        OutboxEventEntity first = createOutboxEvent("evt-aggregate-1", "article", "A-100", createdAt);
        OutboxEventEntity second = createOutboxEvent("evt-aggregate-2", "article", "A-100", createdAt);
        createOutboxEvent("evt-aggregate-3", "article", "A-101", createdAt);

        List<OutboxEventEntity> ascending = outboxRepository.findByAggregateTypeAndAggregateIdAndStatusIn(
                "article",
                "A-100",
                EnumSet.of(OutboxEventStatus.FAILED),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt"))
        );

        assertThat(ascending)
                .extracting(OutboxEventEntity::getId)
                .containsExactly(first.getId(), second.getId());

        List<OutboxEventEntity> descending = outboxRepository.findByAggregateTypeAndAggregateIdAndStatusIn(
                "article",
                "A-100",
                EnumSet.of(OutboxEventStatus.FAILED),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(descending)
                .extracting(OutboxEventEntity::getId)
                .containsExactly(second.getId(), first.getId());
    }

    private ContentDraftEntity createDraft(String bizNo) {
        ContentDraftEntity draft = new ContentDraftEntity();
        draft.setBizNo(bizNo);
        draft.setTitle("t1");
        draft.setSummary("s1");
        draft.setBody("b1");
        draft.setDraftVersion(1);
        draft.setPublishedVersion(0);
        draft.setWorkflowStatus(WorkflowStatus.DRAFT);
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        draft.prepareForInsert();
        draftMapper.insert(draft);
        return draft;
    }

    private OutboxEventEntity createOutboxEvent(String eventId,
                                                String aggregateType,
                                                String aggregateId,
                                                LocalDateTime createdAt) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setEventId(eventId);
        event.setEventType("CONTENT_PUBLISHED");
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setAggregateVersion(1);
        event.setExchangeName("workflow.exchange");
        event.setRoutingKey("workflow.content.published");
        event.setPayloadJson("{\"eventId\":\"" + eventId + "\"}");
        event.setHeadersJson("{}");
        event.setStatus(OutboxEventStatus.FAILED);
        event.setAttempt(1);
        event.setCreatedAt(createdAt);
        event.setUpdatedAt(createdAt);
        return outboxRepository.save(event);
    }
}
