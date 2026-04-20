package com.contentworkflow.common.security;

import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "workflow.security.login")
public class WorkflowLoginProperties {

    private List<DemoUser> demoUsers = new ArrayList<>();

    public List<DemoUser> getDemoUsers() {
        return demoUsers;
    }

    public void setDemoUsers(List<DemoUser> demoUsers) {
        this.demoUsers = demoUsers == null ? new ArrayList<>() : new ArrayList<>(demoUsers);
    }

    public static class DemoUser {

        private String username;
        private String password;
        private String operatorId;
        private String operatorName;
        private List<WorkflowRole> roles = new ArrayList<>();
        private boolean enabled = true;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getOperatorId() {
            return operatorId;
        }

        public void setOperatorId(String operatorId) {
            this.operatorId = operatorId;
        }

        public String getOperatorName() {
            return operatorName;
        }

        public void setOperatorName(String operatorName) {
            this.operatorName = operatorName;
        }

        public List<WorkflowRole> getRoles() {
            return roles;
        }

        public void setRoles(List<WorkflowRole> roles) {
            this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
