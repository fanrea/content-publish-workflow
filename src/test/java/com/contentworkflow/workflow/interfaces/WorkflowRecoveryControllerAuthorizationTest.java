package com.contentworkflow.workflow.interfaces;

import com.contentworkflow.common.messaging.outbox.OutboxEventStatus;
import com.contentworkflow.common.web.GlobalExceptionHandler;
import com.contentworkflow.common.web.auth.CurrentWorkflowOperatorArgumentResolver;
import com.contentworkflow.common.web.auth.WorkflowAuthConstants;
import com.contentworkflow.common.web.auth.WorkflowAuthorizationInterceptor;
import com.contentworkflow.common.web.auth.WorkflowOperatorResolver;
import com.contentworkflow.common.web.auth.WorkflowPermissionPolicy;
import com.contentworkflow.workflow.application.task.WorkflowRecoveryService;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.interfaces.vo.ManualRecoveryResponse;
import com.contentworkflow.workflow.interfaces.vo.RecoverableOutboxEventResponse;
import com.contentworkflow.workflow.interfaces.vo.RecoverablePublishTaskResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 测试类，用于验证当前模块在特定场景下的行为、状态变化或边界条件。
 */

class WorkflowRecoveryControllerAuthorizationTest {

    private static final String OP_ID = "10001";
    private static final String OP_NAME = "alice";

    private MockMvc mockMvc;

    /**
     * 执行测试前的初始化逻辑，为后续测试用例准备运行环境。
     */

    @BeforeEach
    void setUp() {
        WorkflowRecoveryService service = mock(WorkflowRecoveryService.class);
        when(service.listRecoverablePublishTasks(anyLong(), any()))
                .thenReturn(List.of(new RecoverablePublishTaskResponse(
                        1L,
                        3,
                        com.contentworkflow.workflow.domain.enums.PublishTaskType.REFRESH_SEARCH_INDEX,
                        PublishTaskStatus.DEAD,
                        3,
                        "boom",
                        LocalDateTime.now(),
                        null,
                        null,
                        true,
                        false,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )));
        when(service.retryPublishTask(anyLong(), anyLong(), any(), any()))
                .thenReturn(new ManualRecoveryResponse(
                        "PUBLISH_TASK",
                        1L,
                        1L,
                        "DEAD",
                        "PENDING",
                        OP_NAME,
                        null,
                        LocalDateTime.now()
                ));
        when(service.listRecoverableOutboxEvents(any(), any(), anyInt()))
                .thenReturn(List.of(new RecoverableOutboxEventResponse(
                        9L,
                        "evt-9",
                        "SEARCH_INDEX_REFRESH_REQUESTED",
                        "content_draft",
                        "1",
                        3,
                        OutboxEventStatus.DEAD,
                        5,
                        "broken",
                        LocalDateTime.now(),
                        null,
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        true
                )));

        WorkflowPermissionPolicy permissionPolicy = new WorkflowPermissionPolicy();
        WorkflowOperatorResolver resolver = new WorkflowOperatorResolver(permissionPolicy);
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkflowRecoveryController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new WorkflowAuthorizationInterceptor(resolver, permissionPolicy))
                .setCustomArgumentResolvers(new CurrentWorkflowOperatorArgumentResolver(resolver))
                .build();
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     */

    @Test
    void listRecoverablePublishTasks_shouldRequireTaskViewPermission() throws Exception {
        mockMvc.perform(get("/api/workflows/drafts/1/recovery/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/workflows/drafts/1/recovery/tasks")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "EDITOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/workflows/drafts/1/recovery/tasks")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    /**
     * 处理 retry publish task_should require manual recovery permission 相关逻辑，并返回对应的执行结果。
     */

    @Test
    void retryPublishTask_shouldRequireManualRecoveryPermission() throws Exception {
        mockMvc.perform(post("/api/workflows/drafts/1/tasks/2/manual-retry"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/workflows/drafts/1/tasks/2/manual-retry")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "REVIEWER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/workflows/drafts/1/tasks/2/manual-retry")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     */

    @Test
    void listRecoverableOutboxEvents_shouldRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/workflows/outbox/events/recovery")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/workflows/outbox/events/recovery")
                        .header(WorkflowAuthConstants.OPERATOR_ID_HEADER, OP_ID)
                        .header(WorkflowAuthConstants.OPERATOR_NAME_HEADER, OP_NAME)
                        .header(WorkflowAuthConstants.ROLE_HEADER, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }
}
