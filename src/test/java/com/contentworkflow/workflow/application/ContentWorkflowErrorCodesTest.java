package com.contentworkflow.workflow.application;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.workflow.application.store.InMemoryWorkflowStore;
import com.contentworkflow.workflow.interfaces.dto.CreateDraftRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.contentworkflow.testing.BusinessExceptionAssertions.assertCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 测试类，用于验证当前模块在特定场景下的行为、状态变化或边界条件。
 */

class ContentWorkflowErrorCodesTest {

    private ContentWorkflowService service;

    /**
     * 执行测试前的初始化逻辑，为后续测试用例准备运行环境。
     */

    @BeforeEach
    void setUp() {
        service = new ContentWorkflowApplicationService(new InMemoryWorkflowStore());
    }

    /**
     * 处理 draft_not_found_is_stable_error_code 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void draft_not_found_is_stable_error_code() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getDraft(404L));
        assertCode(ex, "DRAFT_NOT_FOUND");
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     */

    @Test
    void list_reviews_requires_existing_draft() {
        Long id = service.createDraft(new CreateDraftRequest("t", "s", "b")).id();
        service.listReviews(id);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.listReviews(9999L));
        assertCode(ex, "DRAFT_NOT_FOUND");
    }
}
