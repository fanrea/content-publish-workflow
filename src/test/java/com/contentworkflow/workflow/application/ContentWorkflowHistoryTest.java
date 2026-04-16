package com.contentworkflow.workflow.application;

import com.contentworkflow.workflow.domain.enums.ReviewDecision;
import com.contentworkflow.workflow.interfaces.dto.CreateDraftRequest;
import com.contentworkflow.workflow.interfaces.dto.ReviewDecisionRequest;
import com.contentworkflow.workflow.interfaces.dto.SubmitReviewRequest;
import com.contentworkflow.workflow.interfaces.dto.UpdateDraftRequest;
import com.contentworkflow.workflow.interfaces.vo.ReviewRecordResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试类，用于验证当前模块在特定场景下的行为、状态变化或边界条件。
 */

class ContentWorkflowHistoryTest {

    private ContentWorkflowService service;

    /**
     * 执行测试前的初始化逻辑，为后续测试用例准备运行环境。
     */

    @BeforeEach
    void setUp() {
        service = new InMemoryContentWorkflowService();
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     */

    @Test
    void list_reviews_returns_latest_first() throws Exception {
        Long id = service.createDraft(new CreateDraftRequest("t", "s", "b")).id();

        service.submitReview(id, new SubmitReviewRequest("editor", "v1"));
        service.review(id, new ReviewDecisionRequest("reviewer", ReviewDecision.REJECT, "needs change"));

        // Make sure timestamps differ so sorting by reviewedAt is deterministic.
        Thread.sleep(30);

        service.updateDraft(id, new UpdateDraftRequest("t2", "s2", "b2"));
        service.submitReview(id, new SubmitReviewRequest("editor", "v2"));
        service.review(id, new ReviewDecisionRequest("reviewer", ReviewDecision.APPROVE, "ok"));

        List<ReviewRecordResponse> records = service.listReviews(id);
        assertTrue(records.size() >= 2);
        assertEquals(ReviewDecision.APPROVE, records.get(0).decision());
        assertEquals(ReviewDecision.REJECT, records.get(1).decision());
        assertEquals(2, records.get(0).draftVersion(), "second review should be on draft version 2");
        assertEquals(1, records.get(1).draftVersion());
    }
}

