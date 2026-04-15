package com.contentworkflow.workflow.application;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.workflow.interfaces.dto.CreateDraftRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.contentworkflow.testing.BusinessExceptionAssertions.assertCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentWorkflowErrorCodesTest {

    private ContentWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryContentWorkflowService();
    }

    @Test
    void draft_not_found_is_stable_error_code() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getDraft(404L));
        assertCode(ex, "DRAFT_NOT_FOUND");
    }

    @Test
    void list_reviews_requires_existing_draft() {
        Long id = service.createDraft(new CreateDraftRequest("t", "s", "b")).id();
        service.listReviews(id);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.listReviews(9999L));
        assertCode(ex, "DRAFT_NOT_FOUND");
    }
}

