package com.contentworkflow.workflow.infrastructure.persistence;

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

import static org.assertj.core.api.Assertions.assertThat;

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
}
