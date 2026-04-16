package com.contentworkflow.workflow.interfaces;

import com.contentworkflow.common.api.ApiResponse;
import com.contentworkflow.common.messaging.outbox.OutboxEventStatus;
import com.contentworkflow.common.web.auth.CurrentWorkflowOperator;
import com.contentworkflow.common.web.auth.RequireWorkflowPermission;
import com.contentworkflow.common.web.auth.RequireWorkflowRole;
import com.contentworkflow.common.web.auth.WorkflowOperatorIdentity;
import com.contentworkflow.common.web.auth.WorkflowPermission;
import com.contentworkflow.workflow.application.task.WorkflowRecoveryService;
import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import com.contentworkflow.workflow.interfaces.dto.ManualRecoveryRequest;
import com.contentworkflow.workflow.interfaces.vo.ManualRecoveryResponse;
import com.contentworkflow.workflow.interfaces.vo.RecoverableOutboxEventResponse;
import com.contentworkflow.workflow.interfaces.vo.RecoverablePublishTaskResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 接口层控制器，负责接收 HTTP 请求、完成参数校验与权限约束，并将业务处理委托给应用服务。
 */
@RestController
@Validated
@RequestMapping("/api/workflows")
public class WorkflowRecoveryController {

    private final WorkflowRecoveryService workflowRecoveryService;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param workflowRecoveryService 参数 workflowRecoveryService 对应的业务输入值
     */

    public WorkflowRecoveryController(WorkflowRecoveryService workflowRecoveryService) {
        this.workflowRecoveryService = workflowRecoveryService;
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @param statuses 参数 statuses 对应的业务输入值
     * @return 统一封装后的接口响应结果
     */

    @GetMapping("/drafts/{draftId}/recovery/tasks")
    @RequireWorkflowPermission({WorkflowPermission.TASK_VIEW})
    public ApiResponse<List<RecoverablePublishTaskResponse>> listRecoverablePublishTasks(
            @PathVariable @Min(1) Long draftId,
            @RequestParam(value = "status", required = false) List<PublishTaskStatus> statuses
    ) {
        return ApiResponse.ok(workflowRecoveryService.listRecoverablePublishTasks(draftId, statuses));
    }

    /**
     * 处理 retry publish task 相关逻辑，并返回对应的执行结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param taskId 相关业务对象的唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 统一封装后的接口响应结果
     */

    @PostMapping("/drafts/{draftId}/tasks/{taskId}/manual-retry")
    @RequireWorkflowPermission({WorkflowPermission.TASK_MANUAL_REQUEUE})
    public ApiResponse<ManualRecoveryResponse> retryPublishTask(@PathVariable @Min(1) Long draftId,
                                                                @PathVariable @Min(1) Long taskId,
                                                                @RequestBody(required = false) @Valid ManualRecoveryRequest request,
                                                                @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        String remark = request == null ? null : request.remark();
        return ApiResponse.ok(workflowRecoveryService.retryPublishTask(draftId, taskId, remark, operator));
    }

    /**
     * 处理 retry current version publish tasks 相关逻辑，并返回对应的执行结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 统一封装后的接口响应结果
     */

    @PostMapping("/drafts/{draftId}/tasks/manual-retry-current-version")
    @RequireWorkflowPermission({WorkflowPermission.TASK_MANUAL_REQUEUE})
    public ApiResponse<List<ManualRecoveryResponse>> retryCurrentVersionPublishTasks(
            @PathVariable @Min(1) Long draftId,
            @RequestBody(required = false) @Valid ManualRecoveryRequest request,
            @CurrentWorkflowOperator WorkflowOperatorIdentity operator
    ) {
        String remark = request == null ? null : request.remark();
        return ApiResponse.ok(workflowRecoveryService.retryCurrentVersionPublishTasks(draftId, remark, operator));
    }

    /**
     * 处理 requeue dead publish task 相关逻辑，并返回对应的执行结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param taskId 相关业务对象的唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 统一封装后的接口响应结果
     */

    @PostMapping("/drafts/{draftId}/tasks/{taskId}/manual-requeue")
    @RequireWorkflowPermission({WorkflowPermission.TASK_MANUAL_REQUEUE})
    public ApiResponse<ManualRecoveryResponse> requeueDeadPublishTask(@PathVariable @Min(1) Long draftId,
                                                                      @PathVariable @Min(1) Long taskId,
                                                                      @RequestBody(required = false) @Valid ManualRecoveryRequest request,
                                                                      @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        String remark = request == null ? null : request.remark();
        return ApiResponse.ok(workflowRecoveryService.requeueDeadPublishTask(draftId, taskId, remark, operator));
    }

    /**
     * 查询并返回符合条件的数据列表，供上层流程继续处理或展示。
     *
     * @param draftId 草稿唯一标识
     * @param statuses 参数 statuses 对应的业务输入值
     * @param limit 参数 limit 对应的业务输入值
     * @return 统一封装后的接口响应结果
     */

    @GetMapping("/outbox/events/recovery")
    @RequireWorkflowRole({WorkflowRole.ADMIN})
    @RequireWorkflowPermission({WorkflowPermission.OUTBOX_MANUAL_REQUEUE})
    public ApiResponse<List<RecoverableOutboxEventResponse>> listRecoverableOutboxEvents(
            @RequestParam(value = "draftId", required = false) @Min(1) Long draftId,
            @RequestParam(value = "status", required = false) List<OutboxEventStatus> statuses,
            @RequestParam(value = "limit", defaultValue = "50") @Min(1) @Max(200) Integer limit
    ) {
        return ApiResponse.ok(workflowRecoveryService.listRecoverableOutboxEvents(draftId, statuses, limit));
    }

    /**
     * 处理 retry outbox event 相关逻辑，并返回对应的执行结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param outboxEventId 相关业务对象的唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 统一封装后的接口响应结果
     */

    @PostMapping("/outbox/events/{outboxEventId}/manual-retry")
    @RequireWorkflowRole({WorkflowRole.ADMIN})
    @RequireWorkflowPermission({WorkflowPermission.OUTBOX_MANUAL_REQUEUE})
    public ApiResponse<ManualRecoveryResponse> retryOutboxEvent(@PathVariable @Min(1) Long outboxEventId,
                                                                @RequestBody(required = false) @Valid ManualRecoveryRequest request,
                                                                @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        String remark = request == null ? null : request.remark();
        return ApiResponse.ok(workflowRecoveryService.retryOutboxEvent(outboxEventId, remark, operator));
    }

    /**
     * 处理 requeue dead outbox event 相关逻辑，并返回对应的执行结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param outboxEventId 相关业务对象的唯一标识
     * @param request 封装业务输入的请求对象
     * @param operator 当前操作人身份信息
     * @return 统一封装后的接口响应结果
     */

    @PostMapping("/outbox/events/{outboxEventId}/manual-requeue")
    @RequireWorkflowRole({WorkflowRole.ADMIN})
    @RequireWorkflowPermission({WorkflowPermission.OUTBOX_MANUAL_REQUEUE})
    public ApiResponse<ManualRecoveryResponse> requeueDeadOutboxEvent(@PathVariable @Min(1) Long outboxEventId,
                                                                      @RequestBody(required = false) @Valid ManualRecoveryRequest request,
                                                                      @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        String remark = request == null ? null : request.remark();
        return ApiResponse.ok(workflowRecoveryService.requeueDeadOutboxEvent(outboxEventId, remark, operator));
    }
}
