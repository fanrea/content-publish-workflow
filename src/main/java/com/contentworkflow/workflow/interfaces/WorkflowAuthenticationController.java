package com.contentworkflow.workflow.interfaces;

import com.contentworkflow.common.api.ApiResponse;
import com.contentworkflow.common.security.WorkflowLoginService;
import com.contentworkflow.workflow.interfaces.dto.WorkflowLoginRequest;
import com.contentworkflow.workflow.interfaces.vo.WorkflowLoginResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class WorkflowAuthenticationController {

    private final WorkflowLoginService workflowLoginService;

    public WorkflowAuthenticationController(WorkflowLoginService workflowLoginService) {
        this.workflowLoginService = workflowLoginService;
    }

    @PostMapping("/login")
    public ApiResponse<WorkflowLoginResponse> login(@RequestBody @Valid WorkflowLoginRequest request) {
        return ApiResponse.ok(WorkflowLoginResponse.from(
                workflowLoginService.login(request.username(), request.password())
        ));
    }
}
