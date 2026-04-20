package com.contentworkflow.common.security;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.common.web.auth.WorkflowAuthConstants;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class WorkflowJwtAuthenticationFilter extends OncePerRequestFilter {

    private final WorkflowJwtTokenService tokenService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public WorkflowJwtAuthenticationFilter(WorkflowJwtTokenService tokenService,
                                           WorkflowAuthenticationEntryPoint authenticationEntryPoint) {
        this.tokenService = tokenService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(WorkflowAuthConstants.AUTHORIZATION_HEADER);
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith(WorkflowAuthConstants.BEARER_PREFIX)) {
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("invalid authorization header"));
            return;
        }

        String token = authorization.substring(WorkflowAuthConstants.BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("missing bearer token"));
            return;
        }

        try {
            Authentication authentication = tokenService.authenticate(token);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException | BusinessException ex) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("invalid jwt token", ex));
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, ex);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
