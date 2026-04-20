package com.contentworkflow.workflow.interfaces;

import com.contentworkflow.common.api.PageResponse;
import com.contentworkflow.common.security.WorkflowAuthenticationEntryPoint;
import com.contentworkflow.common.security.WorkflowJwtAuthenticationFilter;
import com.contentworkflow.common.security.WorkflowJwtTokenService;
import com.contentworkflow.common.web.GlobalExceptionHandler;
import com.contentworkflow.common.web.auth.CurrentWorkflowOperatorArgumentResolver;
import com.contentworkflow.common.web.auth.WorkflowAuthorizationInterceptor;
import com.contentworkflow.common.web.auth.WorkflowOperatorIdentity;
import com.contentworkflow.common.web.auth.WorkflowOperatorResolver;
import com.contentworkflow.common.web.auth.WorkflowPermissionPolicy;
import com.contentworkflow.testing.WorkflowJwtTestSupport;
import com.contentworkflow.testing.WorkflowSecurityTestSupport;
import com.contentworkflow.workflow.application.ContentWorkflowService;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.interfaces.dto.CreateDraftRequest;
import com.contentworkflow.workflow.interfaces.dto.DraftQueryRequest;
import com.contentworkflow.workflow.interfaces.vo.ContentDraftResponse;
import com.contentworkflow.workflow.interfaces.vo.ContentDraftSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContentWorkflowJwtAuthenticationTest {

    private static final String OP_ID = "10001";
    private static final String OP_NAME = "alice";

    private MockMvc mockMvc;
    private ContentWorkflowService service;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        service = mock(ContentWorkflowService.class);
        when(service.pageDraftSummaries(any(DraftQueryRequest.class))).thenReturn(new PageResponse<>(
                List.of(new ContentDraftSummaryResponse(
                        1L,
                        "CPW-1",
                        "t",
                        "s",
                        1,
                        0,
                        0L,
                        WorkflowStatus.DRAFT,
                        null,
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        null
                )),
                1L,
                1,
                20,
                1
        ));
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

        WorkflowPermissionPolicy permissionPolicy = new WorkflowPermissionPolicy();
        WorkflowOperatorResolver resolver = new WorkflowOperatorResolver(permissionPolicy);
        WorkflowJwtTokenService tokenService = WorkflowJwtTestSupport.tokenService();
        WorkflowJwtAuthenticationFilter jwtFilter = new WorkflowJwtAuthenticationFilter(
                tokenService,
                new WorkflowAuthenticationEntryPoint(JsonMapper.builder().findAndAddModules().build())
        );
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new ContentWorkflowController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentWorkflowOperatorArgumentResolver(resolver))
                .setValidator(validator)
                .addInterceptors(new WorkflowAuthorizationInterceptor(resolver, permissionPolicy))
                .addFilters(jwtFilter)
                .alwaysDo(result -> WorkflowSecurityTestSupport.clearSecurityContext())
                .build();
    }

    @AfterEach
    void tearDown() {
        WorkflowSecurityTestSupport.clearSecurityContext();
        if (validator != null) {
            validator.close();
        }
    }

    @Test
    void protectedEndpoint_shouldRejectMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/workflows/drafts/page"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(service);
    }

    @Test
    void protectedEndpoint_shouldRejectMalformedAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/workflows/drafts/page")
                        .header("Authorization", "Token invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(service);
    }

    @Test
    void createDraft_shouldAuthenticateViaJwtAndInjectOperator() throws Exception {
        String token = WorkflowJwtTestSupport.bearerToken(OP_ID, OP_NAME, WorkflowRole.EDITOR);

        mockMvc.perform(post("/api/workflows/drafts")
                        .header("Authorization", "Bearer " + token)
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

    @Test
    void createDraft_shouldRejectAuthenticatedButUnauthorizedRole() throws Exception {
        String token = WorkflowJwtTestSupport.bearerToken(OP_ID, OP_NAME, WorkflowRole.REVIEWER);

        mockMvc.perform(post("/api/workflows/drafts")
                        .header("Authorization", "Bearer " + token)
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
}
