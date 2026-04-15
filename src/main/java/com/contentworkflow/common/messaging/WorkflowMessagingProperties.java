package com.contentworkflow.common.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbox + RabbitMQ related properties.
 */
@ConfigurationProperties(prefix = "workflow.outbox")
public class WorkflowMessagingProperties {

    /**
     * Enable outbox persistence. Business code writes events to DB outbox inside the main
     * transaction.
     */
    private boolean enabled = false;

    /**
     * Exchange used by outbox relay.
     */
    private String exchange = "cpw.workflow.events";

    /**
     * Routing key prefix. Event type names are converted from underscore to dot and appended after
     * this prefix.
     */
    private String routingKeyPrefix = "content.";

    private final Relay relay = new Relay();
    private final Topology topology = new Topology();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getRoutingKeyPrefix() {
        return routingKeyPrefix;
    }

    public void setRoutingKeyPrefix(String routingKeyPrefix) {
        this.routingKeyPrefix = routingKeyPrefix;
    }

    public Relay getRelay() {
        return relay;
    }

    public Topology getTopology() {
        return topology;
    }

    public static class Relay {

        private boolean enabled = false;
        private long pollDelayMs = 1000;
        private int batchSize = 50;
        private int lockSeconds = 60;
        private int maxRetries = 10;
        private int baseDelaySeconds = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getPollDelayMs() {
            return pollDelayMs;
        }

        public void setPollDelayMs(long pollDelayMs) {
            this.pollDelayMs = pollDelayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getLockSeconds() {
            return lockSeconds;
        }

        public void setLockSeconds(int lockSeconds) {
            this.lockSeconds = lockSeconds;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getBaseDelaySeconds() {
            return baseDelaySeconds;
        }

        public void setBaseDelaySeconds(int baseDelaySeconds) {
            this.baseDelaySeconds = baseDelaySeconds;
        }
    }

    public static class Topology {

        /**
         * Declare workflow exchange / queues / bindings on startup.
         */
        private boolean enabled = false;

        private String searchIndexQueue = "cpw.workflow.search-index.refresh";
        private String readModelQueue = "cpw.workflow.read-model.sync";
        private String notificationQueue = "cpw.workflow.publish.notification";

        private final Consumer consumer = new Consumer();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSearchIndexQueue() {
            return searchIndexQueue;
        }

        public void setSearchIndexQueue(String searchIndexQueue) {
            this.searchIndexQueue = searchIndexQueue;
        }

        public String getReadModelQueue() {
            return readModelQueue;
        }

        public void setReadModelQueue(String readModelQueue) {
            this.readModelQueue = readModelQueue;
        }

        public String getNotificationQueue() {
            return notificationQueue;
        }

        public void setNotificationQueue(String notificationQueue) {
            this.notificationQueue = notificationQueue;
        }

        public Consumer getConsumer() {
            return consumer;
        }
    }

    public static class Consumer {

        /**
         * Enable demo consumers. These listeners only log receipt and represent replaceable
         * downstream integrations.
         */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
