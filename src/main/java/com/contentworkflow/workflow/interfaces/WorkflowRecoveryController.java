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
 * Operational recovery endpoints for manual retry / requeue.
 */
@RestController
@Validated
@RequestMapping("/api/workflows")
public class WorkflowRecoveryController {

    private final WorkflowRecoveryService workflowRecoveryService;

    public WorkflowRecoveryController(WorkflowRecoveryService workflowRecoveryService) {
        this.workflowRecoveryService = workflowRecoveryService;
    }

    @GetMapping("/drafts/{draftId}/recovery/tasks")
    @RequireWorkflowPermission({WorkflowPermission.TASK_VIEW})
    public ApiResponse<List<RecoverablePublishTaskResponse>> listRecoverablePublishTasks(
            @PathVariable @Min(1) Long draftId,
            @RequestParam(value = "status", required = false) List<PublishTaskStatus> statuses
    ) {
        return ApiResponse.ok(workflowRecoveryService.listRecoverablePublishTasks(draftId, statuses));
    }

    @PostMapping("/drafts/{draftId}/tasks/{taskId}/manual-retry")
    @RequireWorkflowPermission({WorkflowPermission.TASK_MANUAL_REQUEUE})
    public ApiResponse<ManualRecoveryResponse> retryPublishTask(@PathVariable @Min(1) Long draftId,
                                                                @PathVariable @Min(1) Long taskId,
                                                                @RequestBody(required = false) @Valid ManualRecoveryRequest request,
                                                                @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        String remark = request == null ? null : request.remark();
        return ApiResponse.ok(workflowRecoveryService.retryPublishTask(draftId, taskId, remark, operator));
    }

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

    @PostMapping("/drafts/{draftId}/tasks/{taskId}/manual-requeue")
    @RequireWorkflowPermission({WorkflowPermission.TASK_MANUAL_REQUEUE})
    public ApiResponse<ManualRecoveryResponse> requeueDeadPublishTask(@PathVariable @Min(1) Long draftId,
                                                                      @PathVariable @Min(1) Long taskId,
                                                                      @RequestBody(required = false) @Valid ManualRecoveryRequest request,
                                                                      @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        String remark = request == null ? null : request.remark();
        return ApiResponse.ok(workflowRecoveryService.requeueDeadPublishTask(draftId, taskId, remark, operator));
    }

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

    @PostMapping("/outbox/events/{outboxEventId}/manual-retry")
    @RequireWorkflowRole({WorkflowRole.ADMIN})
    @RequireWorkflowPermission({WorkflowPermission.OUTBOX_MANUAL_REQUEUE})
    public ApiResponse<ManualRecoveryResponse> retryOutboxEvent(@PathVariable @Min(1) Long outboxEventId,
                                                                @RequestBody(required = false) @Valid ManualRecoveryRequest request,
                                                                @CurrentWorkflowOperator WorkflowOperatorIdentity operator) {
        String remark = request == null ? null : request.remark();
        return ApiResponse.ok(workflowRecoveryService.retryOutboxEvent(outboxEventId, remark, operator));
    }

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
