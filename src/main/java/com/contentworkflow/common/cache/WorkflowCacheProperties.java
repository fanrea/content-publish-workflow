package com.contentworkflow.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "workflow.cache")
public class WorkflowCacheProperties {

    private String keyPrefix = "cpw:";
    private Duration draftDetailTtl = Duration.ofSeconds(30);
    private Duration draftStatusCountTtl = Duration.ofSeconds(10);
    private Duration draftListTtl = Duration.ofSeconds(5);
    private boolean twoLevelEnabled = false;
    private final Local local = new Local();

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getDraftDetailTtl() {
        return draftDetailTtl;
    }

    public void setDraftDetailTtl(Duration draftDetailTtl) {
        this.draftDetailTtl = draftDetailTtl;
    }

    public Duration getDraftStatusCountTtl() {
        return draftStatusCountTtl;
    }

    public void setDraftStatusCountTtl(Duration draftStatusCountTtl) {
        this.draftStatusCountTtl = draftStatusCountTtl;
    }

    public Duration getDraftListTtl() {
        return draftListTtl;
    }

    public void setDraftListTtl(Duration draftListTtl) {
        this.draftListTtl = draftListTtl;
    }

    public boolean isTwoLevelEnabled() {
        return twoLevelEnabled;
    }

    public void setTwoLevelEnabled(boolean twoLevelEnabled) {
        this.twoLevelEnabled = twoLevelEnabled;
    }

    public Local getLocal() {
        return local;
    }

    public static class Local {

        private int initialCapacity = 256;
        private long maximumSize = 5_000;
        private Duration expireAfterWrite = Duration.ofMinutes(10);
        private boolean recordStats = true;

        public int getInitialCapacity() {
            return initialCapacity;
        }

        public void setInitialCapacity(int initialCapacity) {
            this.initialCapacity = initialCapacity;
        }

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }

        public Duration getExpireAfterWrite() {
            return expireAfterWrite;
        }

        public void setExpireAfterWrite(Duration expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
        }

        public boolean isRecordStats() {
            return recordStats;
        }

        public void setRecordStats(boolean recordStats) {
            this.recordStats = recordStats;
        }
    }
}
