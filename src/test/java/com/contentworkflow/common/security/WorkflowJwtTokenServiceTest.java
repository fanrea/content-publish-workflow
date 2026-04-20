package com.contentworkflow.common.security;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.testing.WorkflowJwtTestSupport;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowJwtTokenServiceTest {

    private final WorkflowJwtTokenService tokenService = WorkflowJwtTestSupport.tokenService();

    @Test
    void createToken_shouldAuthenticatePrincipalAndAuthorities() {
        String token = tokenService.createToken(
                "10001",
                "alice",
                EnumSet.of(WorkflowRole.EDITOR, WorkflowRole.REVIEWER),
                Duration.ofMinutes(5)
        );

        Authentication authentication = tokenService.authenticate(token);

        assertInstanceOf(WorkflowJwtPrincipal.class, authentication.getPrincipal());
        WorkflowJwtPrincipal principal = (WorkflowJwtPrincipal) authentication.getPrincipal();
        assertEquals("10001", principal.operatorId());
        assertEquals("alice", principal.operatorName());
        assertEquals(EnumSet.of(WorkflowRole.EDITOR, WorkflowRole.REVIEWER), principal.roles());
        assertTrue(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_EDITOR")));
        assertTrue(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_REVIEWER")));
    }

    @Test
    void authenticate_shouldRejectTokenWithoutRoles() {
        String token = tokenService.createToken(
                "10001",
                "alice",
                EnumSet.noneOf(WorkflowRole.class),
                Duration.ofMinutes(5)
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> tokenService.authenticate(token));

        assertEquals("UNAUTHORIZED", ex.getCode());
        assertEquals("missing workflow role(s)", ex.getMessage());
    }

    @Test
    void authenticate_shouldRejectTokenWithoutOperatorNameClaim() {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .issuer(WorkflowJwtTestSupport.TEST_ISSUER)
                .subject("10001")
                .claim("operatorId", "10001")
                .claim("roles", java.util.List.of(WorkflowRole.ADMIN.name()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(WorkflowJwtTestSupport.TEST_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        BusinessException ex = assertThrows(BusinessException.class, () -> tokenService.authenticate(token));

        assertEquals("UNAUTHORIZED", ex.getCode());
        assertEquals("invalid workflow jwt claims", ex.getMessage());
    }
}
