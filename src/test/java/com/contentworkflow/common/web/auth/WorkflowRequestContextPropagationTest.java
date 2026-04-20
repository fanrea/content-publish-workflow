package com.contentworkflow.common.web.auth;

import com.contentworkflow.common.api.ApiResponse;
import com.contentworkflow.common.web.GlobalExceptionHandler;
import com.contentworkflow.testing.WorkflowSecurityTestSupport;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.contentworkflow.testing.WorkflowSecurityTestSupport.authenticatedWorkflowOperator;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowRequestContextPropagationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WorkflowPermissionPolicy permissionPolicy = new WorkflowPermissionPolicy();
        WorkflowOperatorResolver resolver = new WorkflowOperatorResolver(permissionPolicy);
        mockMvc = MockMvcBuilders.standaloneSetup(new TestAuthController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentWorkflowOperatorArgumentResolver(resolver))
                .addInterceptors(new WorkflowAuthorizationInterceptor(resolver, permissionPolicy))
                .alwaysDo(result -> WorkflowSecurityTestSupport.clearSecurityContext())
                .build();
    }

    @AfterEach
    void tearDown() {
        WorkflowSecurityTestSupport.clearSecurityContext();
        WorkflowAuditContextHolder.clear();
    }

    @Test
    void requestId_shouldBackfillTraceIdWhenTraceHeaderMissing() throws Exception {
        mockMvc.perform(get("/test/auth/context")
                        .with(authenticatedWorkflowOperator("10001", "alice", WorkflowRole.EDITOR))
                        .header(WorkflowAuthConstants.REQUEST_ID_HEADER, "req-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operatorId").value("10001"))
                .andExpect(jsonPath("$.data.operatorRole").value("EDITOR"))
                .andExpect(jsonPath("$.data.requestId").value("req-1001"))
                .andExpect(jsonPath("$.data.traceId").value("req-1001"))
                .andExpect(jsonPath("$.data.holderRequestId").value("req-1001"))
                .andExpect(jsonPath("$.data.holderTraceId").value("req-1001"));

        assertNull(WorkflowAuditContextHolder.get());
    }

    @Test
    void explicitTraceId_shouldBePreservedIndependentlyFromRequestId() throws Exception {
        mockMvc.perform(get("/test/auth/context")
                        .with(authenticatedWorkflowOperator("10001", "alice", WorkflowRole.EDITOR))
                        .header(WorkflowAuthConstants.REQUEST_ID_HEADER, "req-2002")
                        .header(WorkflowAuthConstants.TRACE_ID_HEADER, "trace-2002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operatorRole").value("EDITOR"))
                .andExpect(jsonPath("$.data.requestId").value("req-2002"))
                .andExpect(jsonPath("$.data.traceId").value("trace-2002"))
                .andExpect(jsonPath("$.data.holderRequestId").value("req-2002"))
                .andExpect(jsonPath("$.data.holderTraceId").value("trace-2002"));

        assertNull(WorkflowAuditContextHolder.get());
    }

    @RestController
    @RequestMapping("/test/auth")
    static class TestAuthController {

        @GetMapping("/context")
        @RequireWorkflowPermission(WorkflowPermission.DRAFT_WRITE)
        ApiResponse<Map<String, Object>> context(@CurrentWorkflowOperator WorkflowOperatorIdentity operator,
                                                 HttpServletRequest request) {
            WorkflowAuditContext context = WorkflowAuditContextHolder.get();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("operatorId", operator.operatorId());
            payload.put("operatorName", operator.operatorName());
            payload.put("operatorRole", operator.role().name());
            payload.put("requestId", request.getAttribute(WorkflowAuthConstants.REQUEST_REQUEST_ID_ATTR));
            payload.put("traceId", request.getAttribute(WorkflowAuthConstants.REQUEST_TRACE_ID_ATTR));
            payload.put("holderRequestId", context == null ? null : context.requestId());
            payload.put("holderTraceId", context == null ? null : context.traceId());
            return ApiResponse.ok(payload);
        }
    }
}
