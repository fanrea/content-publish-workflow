package com.contentworkflow.common.security;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

@Component
public class WorkflowJwtTokenService {

    private static final String ROLES_CLAIM = "roles";
    private static final String OPERATOR_ID_CLAIM = "operatorId";
    private static final String OPERATOR_NAME_CLAIM = "operatorName";

    private final WorkflowJwtProperties properties;

    public WorkflowJwtTokenService(WorkflowJwtProperties properties) {
        this.properties = properties;
    }

    public Authentication authenticate(String token) {
        Jws<Claims> jws = parser().parseSignedClaims(token);
        Claims claims = jws.getPayload();
        String operatorId = claims.get(OPERATOR_ID_CLAIM, String.class);
        String operatorName = claims.get(OPERATOR_NAME_CLAIM, String.class);
        EnumSet<WorkflowRole> roles = parseRoles(claims.get(ROLES_CLAIM, Collection.class));
        if (operatorId == null || operatorId.isBlank() || operatorName == null || operatorName.isBlank()) {
            throw new BusinessException("UNAUTHORIZED", "invalid workflow jwt claims");
        }
        if (roles.isEmpty()) {
            throw new BusinessException("UNAUTHORIZED", "missing workflow role(s)");
        }
        WorkflowJwtPrincipal principal = new WorkflowJwtPrincipal(operatorId.trim(), operatorName.trim(), roles);
        List<GrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .map(GrantedAuthority.class::cast)
                .toList();
        return UsernamePasswordAuthenticationToken.authenticated(principal, token, authorities);
    }

    public String createToken(String operatorId, String operatorName, Collection<WorkflowRole> roles, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl == null ? Duration.ofHours(2) : ttl);
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(operatorId)
                .claim(OPERATOR_ID_CLAIM, operatorId)
                .claim(OPERATOR_NAME_CLAIM, operatorName)
                .claim(ROLES_CLAIM, roles == null ? List.of() : roles.stream().map(Enum::name).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey())
                .compact();
    }

    private JwtParser parser() {
        return Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(properties.getIssuer())
                .clockSkewSeconds(properties.getClockSkewSeconds())
                .build();
    }

    private SecretKey signingKey() {
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("workflow.security.jwt.secret must not be blank");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private EnumSet<WorkflowRole> parseRoles(Collection<?> rawRoles) {
        EnumSet<WorkflowRole> roles = EnumSet.noneOf(WorkflowRole.class);
        if (rawRoles == null) {
            return roles;
        }
        for (Object rawRole : rawRoles) {
            if (rawRole == null) {
                continue;
            }
            roles.add(WorkflowRole.valueOf(String.valueOf(rawRole).trim().toUpperCase()));
        }
        return roles;
    }
}
