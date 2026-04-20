package com.contentworkflow.common.security;

import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class WorkflowDemoAccountService {

    private final Map<String, WorkflowDemoAccount> accountsByUsername;

    public WorkflowDemoAccountService(WorkflowLoginProperties properties) {
        Map<String, WorkflowDemoAccount> accounts = new LinkedHashMap<>();
        for (WorkflowLoginProperties.DemoUser user : properties.getDemoUsers()) {
            if (user == null || isBlank(user.getUsername())) {
                continue;
            }
            String normalizedUsername = normalize(user.getUsername());
            WorkflowDemoAccount account = new WorkflowDemoAccount(
                    user.getUsername().trim(),
                    user.getPassword(),
                    user.getOperatorId(),
                    user.getOperatorName(),
                    user.getRoles() == null || user.getRoles().isEmpty()
                            ? EnumSet.noneOf(WorkflowRole.class)
                            : EnumSet.copyOf(user.getRoles()),
                    user.isEnabled()
            );
            WorkflowDemoAccount previous = accounts.putIfAbsent(normalizedUsername, account);
            if (previous != null) {
                throw new IllegalStateException("duplicate workflow demo user: " + user.getUsername());
            }
        }
        this.accountsByUsername = Map.copyOf(accounts);
    }

    public Optional<WorkflowDemoAccount> findByUsername(String username) {
        if (isBlank(username)) {
            return Optional.empty();
        }
        return Optional.ofNullable(accountsByUsername.get(normalize(username)));
    }

    private String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
