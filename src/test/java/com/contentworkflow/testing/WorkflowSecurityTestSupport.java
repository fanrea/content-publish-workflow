package com.contentworkflow.testing;

import com.contentworkflow.common.security.WorkflowJwtPrincipal;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.EnumSet;

public final class WorkflowSecurityTestSupport {

    private WorkflowSecurityTestSupport() {
    }

    public static RequestPostProcessor authenticatedWorkflowOperator(String operatorId,
                                                                    String operatorName,
                                                                    WorkflowRole... roles) {
        EnumSet<WorkflowRole> roleSet = roles == null || roles.length == 0
                ? EnumSet.noneOf(WorkflowRole.class)
                : EnumSet.copyOf(Arrays.asList(roles));
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                new WorkflowJwtPrincipal(operatorId, operatorName, roleSet),
                "test-token",
                roleSet.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList()
        );
        return request -> setAuthentication(request, authentication);
    }

    public static RequestPostProcessor invalidWorkflowAuthentication() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "invalid-principal",
                "test-token",
                java.util.List.of()
        );
        return request -> setAuthentication(request, authentication);
    }

    public static void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest setAuthentication(MockHttpServletRequest request, Authentication authentication) {
        SecurityContextHolder.clearContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        request.setUserPrincipal(authentication);
        return request;
    }
}
