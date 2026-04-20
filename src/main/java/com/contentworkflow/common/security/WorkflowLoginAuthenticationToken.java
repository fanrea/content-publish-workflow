package com.contentworkflow.common.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

public class WorkflowLoginAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;
    private Object credentials;

    private WorkflowLoginAuthenticationToken(Object principal,
                                             Object credentials,
                                             Collection<? extends GrantedAuthority> authorities,
                                             boolean authenticated) {
        super(authorities == null ? List.of() : authorities);
        this.principal = principal;
        this.credentials = credentials;
        super.setAuthenticated(authenticated);
    }

    public static WorkflowLoginAuthenticationToken unauthenticated(String username, String password) {
        return new WorkflowLoginAuthenticationToken(username, password, List.of(), false);
    }

    public static WorkflowLoginAuthenticationToken authenticated(WorkflowJwtPrincipal principal,
                                                                 Collection<? extends GrantedAuthority> authorities) {
        return new WorkflowLoginAuthenticationToken(principal, null, authorities, true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public void eraseCredentials() {
        credentials = null;
        super.eraseCredentials();
    }
}
