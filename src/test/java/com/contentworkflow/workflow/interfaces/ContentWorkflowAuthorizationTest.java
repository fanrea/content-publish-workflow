package com.contentworkflow.workflow.interfaces;

import com.contentworkflow.common.api.PageResponse;
import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.common.web.GlobalExceptionHandler;
import com.contentworkflow.common.web.auth.CurrentWorkflowOperatorArgumentResolver;
import com.contentworkflow.common.web.auth.WorkflowAuthConstants;
import com.contentworkflow.common.web.auth.WorkflowAuthorizationInterceptor;
import com.contentworkflow.common.web.auth.WorkflowOperatorIdentity;
import com.contentworkflow.common.web.auth.WorkflowPermissionPolicy;
import com.contentworkflow.common.web.auth.WorkflowOperatorResolver;
import com.contentworkflow.workflow.application.ContentWorkflowService;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.interfaces.dto.CreateDraftRequest;
import com.contentworkflow.workflow.interfaces.dto.DraftQueryRequest;
import com.contentworkflow.workflow.interfaces.dto.UpdateDraftRequest;
import com.contentworkflow.workflow.interfaces.vo.ContentDraftResponse;
import com.contentworkflow.workflow.interfaces.vo.ContentDraftSummaryResponse;
import com.contentworkflow.workflow.interfaces.vo.PublishAuditTimelineResponse;
import com.contentworkflow.workflow.interfaces.vo.PublishLogResponse;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditTargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 测试类，用于验证当前模块在特定场景下的行为、状态变化或边界条件。
 */

class ContentWorkflowAuthorizationTest {

    private MockMvc mockMvc;
    private ContentWorkflowService service;

    private static final String OP_ID = "10001";
    private static final String OP_NAME = "alice";

    /**
     * 执行测试前的初始化逻辑，为后续测试用例准备运行环境。
     */

    @BeforeEach
    void setUp() {
        service = mock(ContentWorkflowService.class);
        when(service.listDrafts()).thenReturn(List.of());
        when(service.createDraft(any(CreateDraftRequest.class), any(WorkflowOperatorIdentity.class))).thenReturn(new ContentDraftResponse(
                1L,
                "CPW-1",
                "t",
                "s",
                "b",
                1,
                0,
                0L,
                WorkflowStatus.DRAFT,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        ));
        when(service.pageDraftSummaries(any(DraftQueryRequest.class))).thenReturn(new PageResponse<>(
                List.of(new ContentDraftSummaryResponse(1L, "CPW-1", "t", "s", 1, 0, 0L, WorkflowStatus.DRAFT, null, null, LocalDateTime.now(), LocalDateTime.now(), null)),
                1L,
                1,
                10,
                1
        ));
        when(service.listPublishLogTimeline(any(Long.class), any(String.class))).thenReturn(List.of(
                new PublishLogResponse(
                        1L,
                        "publish:1:1",
                        "req-1",
                        "PUBLISH_REQUESTED",
                        OP_ID,
                        OP_NAME,
                        WorkflowAuditTargetType.CONTENT_DRAFT,
                        1L,
                        1,
                        null,
                        null,
                        "APPROVED",
                        "PUBLISHING",
                        WorkflowAuditResult.ACCEPTED,
                        null,
                        null,
                        "remark",
                        LocalDateTime.now()
                )
        ));
        when(service.getPublishAuditTimeline(any(Long.class), any())).thenReturn(new PublishAuditTimelineResponse(
                1L,
                1,
                "publish:1:1",
                "req-1",
                OP_ID,
                OP_NAME,
                WorkflowStatus.PUBLISHED.name(),
                "PUBLISH_COMPLETED",
                "PUBLISHED",
                WorkflowAuditResult.SUCCESS,
                LocalDateTime.now().minusSeconds(1),
                LocalDateTime.now(),
                2,
                List.of()
        ));

        WorkflowPermissionPolicy permissionPolicy = new WorkflowPermissionPolicy();
        WorkflowOperatorResolver resolver = new WorkflowOperatorResolver(permissionPolicy);
        mockMvc = MockMvcBuilders.standaloneSetup(new ContentWorkflowController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new WorkflowAuthorizationInterceptor(resolver, permissionPolicy))
                .setCustomArgumentResolvers(new CurrentWorkflowOperatorArgumentResolver(resolver))
                .build();
    }

    /**
     * 根据输入参数创建新的业务对象，并返回创建后的最新结果。
     */

    @Test
    void createDraft_shouldRejectWhenRoleHeaderMissing() throws Exception {
        mockMvc.perform(post("/api/workflows/drafts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "t",
                                  "summary": "s",
                                  "body": "b"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    /**
     * 根据输入参数创建新的业务对象，并返回创建后的最新结果。
     */

    @Test
    void createDraft_shouldRejectWhenRoleNotAllowed() throws Exception {
        mockMvc.perform(post("/api/workflows/drafts")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "REVIEWER")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "t",
                                  "summary": "s",
                                  "body": "b"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /**
     * 根据输入参数创建新的业务对象，并返回创建后的最新结果。
     */

    @Test
    void createDraft_shouldAllowEditorRole() throws Exception {
        mockMvc.perform(post("/api/workflows/drafts")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "EDITOR")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "t",
                                  "summary": "s",
                                  "body": "b"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(service).createDraft(any(CreateDraftRequest.class), eq(new WorkflowOperatorIdentity(OP_ID, OP_NAME, WorkflowRole.EDITOR)));
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     */

    @Test
    void listDrafts_shouldRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/workflows/drafts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/workflows/drafts")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "EDITOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/workflows/drafts")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    /**
     * 处理 multi roles header_should work 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void multiRolesHeader_shouldWork() throws Exception {
        mockMvc.perform(post("/api/workflows/drafts")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLES_HEADER, "reviewer, editor")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "t",
                                  "summary": "s",
                                  "body": "b"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    /**
     * 处理 invalid role token_should reject 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void invalidRoleToken_shouldReject() throws Exception {
        mockMvc.perform(post("/api/workflows/drafts")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLES_HEADER, "editor,not_a_role")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "t",
                                  "summary": "s",
                                  "body": "b"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /**
     * 按分页条件查询数据，并返回包含分页元信息的结果。
     */

    @Test
    void updateDraft_shouldMapConcurrentModificationToConflict() throws Exception {
        when(service.updateDraft(eq(1L), any(UpdateDraftRequest.class), any(WorkflowOperatorIdentity.class)))
                .thenThrow(new BusinessException("CONCURRENT_MODIFICATION", "draft changed concurrently"));

        mockMvc.perform(put("/api/workflows/drafts/1")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "EDITOR")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 3,
                                  "title": "t2",
                                  "summary": "s2",
                                  "body": "b2"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void pageEndpoint_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/workflows/drafts/page"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/workflows/drafts/page")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "EDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    /**
     * 处理 log timeline endpoint_should require log view permission 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void logTimelineEndpoint_shouldRequireLogViewPermission() throws Exception {
        mockMvc.perform(get("/api/workflows/drafts/1/logs/timeline")
                        .param("traceId", "publish:1:1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/workflows/drafts/1/logs/timeline")
                        .param("traceId", "publish:1:1")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "EDITOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/workflows/drafts/1/logs/timeline")
                        .param("traceId", "publish:1:1")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。
     */

    @Test
    void publishTimelineEndpoint_shouldRequireLogViewPermission() throws Exception {
        mockMvc.perform(get("/api/workflows/drafts/1/logs/publish-timeline"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/workflows/drafts/1/logs/publish-timeline")
                        .param("publishedVersion", "1")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "EDITOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/workflows/drafts/1/logs/publish-timeline")
                        .param("publishedVersion", "1")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }
}
