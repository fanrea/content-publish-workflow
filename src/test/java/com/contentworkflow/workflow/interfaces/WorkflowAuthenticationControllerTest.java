package com.contentworkflow.workflow.interfaces;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.common.security.WorkflowLoginService;
import com.contentworkflow.common.security.WorkflowLoginSession;
import com.contentworkflow.common.web.GlobalExceptionHandler;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.EnumSet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowAuthenticationControllerTest {

    private MockMvc mockMvc;
    private WorkflowLoginService loginService;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        loginService = mock(WorkflowLoginService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkflowAuthenticationController(loginService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (validator != null) {
            validator.close();
        }
    }

    @Test
    void login_shouldReturnTokenPayload() throws Exception {
        when(loginService.login("admin", "Admin123!")).thenReturn(new WorkflowLoginSession(
                "Bearer",
                "jwt-token",
                Instant.parse("2026-04-18T12:00:00Z"),
                "demo-admin",
                "Demo Admin",
                EnumSet.of(WorkflowRole.ADMIN)
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "Admin123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.data.operatorId").value("demo-admin"))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"));

        verify(loginService).login("admin", "Admin123!");
    }

    @Test
    void login_shouldRejectBlankCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.violations.length()").value(2));

        verifyNoInteractions(loginService);
    }

    @Test
    void login_shouldMapInvalidCredentialsToUnauthorized() throws Exception {
        when(loginService.login("admin", "wrong")).thenThrow(new BusinessException("UNAUTHORIZED", "invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "wrong"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("invalid username or password"));
    }
}
