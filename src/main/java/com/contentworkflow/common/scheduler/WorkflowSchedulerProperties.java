package com.contentworkflow.common.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workflow.scheduler")
public class WorkflowSchedulerProperties {

    private boolean startupReportEnabled = true;
    private String productionRecommended = "xxl-job";
    private final Local local = new Local();

    public boolean isStartupReportEnabled() {
        return startupReportEnabled;
    }

    public void setStartupReportEnabled(boolean startupReportEnabled) {
        this.startupReportEnabled = startupReportEnabled;
    }

    public String getProductionRecommended() {
        return productionRecommended;
    }

    public void setProductionRecommended(String productionRecommended) {
        this.productionRecommended = productionRecommended;
    }

    public Local getLocal() {
        return local;
    }

    public TriggerMode resolveTriggerMode(boolean xxlExecutorEnabled) {
        if (local.enabled && xxlExecutorEnabled) {
            return TriggerMode.HYBRID;
        }
        if (xxlExecutorEnabled) {
            return TriggerMode.XXL_JOB;
        }
        if (local.enabled) {
            return TriggerMode.LOCAL;
        }
        return TriggerMode.NONE;
    }

    public enum TriggerMode {
        LOCAL,
        XXL_JOB,
        HYBRID,
        NONE
    }

    public static class Local {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
