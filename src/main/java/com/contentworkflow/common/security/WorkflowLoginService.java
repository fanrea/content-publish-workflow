package com.contentworkflow.common.security;

import com.contentworkflow.common.exception.BusinessException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class WorkflowLoginService {

    private final AuthenticationManager authenticationManager;
    private final WorkflowJwtTokenService tokenService;
    private final WorkflowJwtProperties jwtProperties;

    public WorkflowLoginService(AuthenticationManager authenticationManager,
                                WorkflowJwtTokenService tokenService,
                                WorkflowJwtProperties jwtProperties) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.jwtProperties = jwtProperties;
    }

    public WorkflowLoginSession login(String username, String password) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    WorkflowLoginAuthenticationToken.unauthenticated(username, password)
            );
        } catch (AuthenticationException ex) {
            throw new BusinessException("UNAUTHORIZED", "invalid username or password");
        }
        WorkflowJwtPrincipal principal = (WorkflowJwtPrincipal) authentication.getPrincipal();
        Instant expiresAt = Instant.now().plus(jwtProperties.getAccessTokenTtl());
        String accessToken = tokenService.createToken(
                principal.operatorId(),
                principal.operatorName(),
                principal.roles(),
                jwtProperties.getAccessTokenTtl()
        );
        return new WorkflowLoginSession(
                "Bearer",
                accessToken,
                expiresAt,
                principal.operatorId(),
                principal.operatorName(),
                principal.roles()
        );
    }
}
