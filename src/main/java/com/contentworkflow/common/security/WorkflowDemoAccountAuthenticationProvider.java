package com.contentworkflow.common.security;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WorkflowDemoAccountAuthenticationProvider implements AuthenticationProvider {

    private final WorkflowDemoAccountService demoAccountService;
    private final PasswordEncoder passwordEncoder;

    public WorkflowDemoAccountAuthenticationProvider(WorkflowDemoAccountService demoAccountService,
                                                     PasswordEncoder passwordEncoder) {
        this.demoAccountService = demoAccountService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!(authentication instanceof WorkflowLoginAuthenticationToken loginToken)) {
            return null;
        }

        String username = String.valueOf(loginToken.getPrincipal());
        String password = loginToken.getCredentials() == null ? "" : String.valueOf(loginToken.getCredentials());

        WorkflowDemoAccount account = demoAccountService.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("invalid username or password"));
        if (!account.enabled()) {
            throw new DisabledException("workflow demo account disabled");
        }
        if (isBlank(account.password())
                || isBlank(account.operatorId())
                || isBlank(account.operatorName())) {
            throw new BusinessException("UNAUTHORIZED", "workflow demo account is incomplete");
        }
        if (!passwordEncoder.matches(password, account.password())) {
            throw new BadCredentialsException("invalid username or password");
        }
        if (account.roles().isEmpty()) {
            throw new BadCredentialsException("workflow demo account has no roles");
        }

        WorkflowJwtPrincipal principal = new WorkflowJwtPrincipal(
                account.operatorId(),
                account.operatorName(),
                account.roles()
        );
        List<GrantedAuthority> authorities = account.roles().stream()
                .map(WorkflowRole::name)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .map(GrantedAuthority.class::cast)
                .toList();
        WorkflowLoginAuthenticationToken authenticated =
                WorkflowLoginAuthenticationToken.authenticated(principal, authorities);
        authenticated.setDetails(loginToken.getDetails());
        return authenticated;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return WorkflowLoginAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
