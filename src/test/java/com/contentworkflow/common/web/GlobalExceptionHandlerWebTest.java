package com.contentworkflow.common.web;

import com.contentworkflow.common.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerWebTest {

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestExceptionController())
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
    void businessException_shouldMapToTypedApiResponse() throws Exception {
        mockMvc.perform(get("/test/exceptions/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DRAFT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("draft missing"))
                .andExpect(jsonPath("$.path").value("/test/exceptions/business"));
    }

    @Test
    void validationException_shouldMapToBadRequestApiResponse() throws Exception {
        mockMvc.perform(post("/test/exceptions/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("request validation failed"))
                .andExpect(jsonPath("$.violations[0].field").value("title"));
    }

    @Test
    void unexpectedException_shouldMapToInternalErrorApiResponse() throws Exception {
        mockMvc.perform(get("/test/exceptions/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("internal server error"))
                .andExpect(jsonPath("$.path").value("/test/exceptions/unexpected"));
    }

    @Test
    void constraintViolationHandler_shouldMapToBadRequestApiResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test/exceptions/constraint");

        ResponseEntity<WorkflowErrorResponse> response =
                handler.handleConstraintViolation(
                        new ConstraintViolationException("limit must be greater than or equal to 1", java.util.Set.of()),
                        request
                );

        assertEquals(400, response.getStatusCode().value());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
        assertEquals("request validation failed", response.getBody().message());
        assertEquals("/test/exceptions/constraint", response.getBody().path());
    }

    @RestController
    @Validated
    @RequestMapping("/test/exceptions")
    static class TestExceptionController {

        @GetMapping("/business")
        String business() {
            throw new BusinessException("DRAFT_NOT_FOUND", "draft missing");
        }

        @PostMapping("/validation")
        String validation(@RequestBody @Valid TestPayload payload) {
            return payload.title();
        }

        @GetMapping("/unexpected")
        String unexpected() {
            throw new IllegalStateException("boom");
        }
    }

    record TestPayload(@NotBlank String title) {
    }
}
