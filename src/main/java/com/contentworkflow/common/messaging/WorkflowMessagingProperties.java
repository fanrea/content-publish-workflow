package com.contentworkflow.common.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置属性载体，用于绑定外部配置项并向运行时组件提供参数。
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

    /**
     * 判断当前对象是否处于特定状态。
     *
     * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
     */

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 处理 set enabled 相关逻辑，并返回对应的执行结果。
     *
     * @param enabled 参数 enabled 对应的业务输入值
     */

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public String getExchange() {
        return exchange;
    }

    /**
     * 处理 set exchange 相关逻辑，并返回对应的执行结果。
     *
     * @param exchange 参数 exchange 对应的业务输入值
     */

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public String getRoutingKeyPrefix() {
        return routingKeyPrefix;
    }

    /**
     * 处理 set routing key prefix 相关逻辑，并返回对应的执行结果。
     *
     * @param routingKeyPrefix 参数 routingKeyPrefix 对应的业务输入值
     */

    public void setRoutingKeyPrefix(String routingKeyPrefix) {
        this.routingKeyPrefix = routingKeyPrefix;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public Relay getRelay() {
        return relay;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public Topology getTopology() {
        return topology;
    }

    /**
     * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
     */

    public static class Relay {

        private boolean enabled = false;
        private long pollDelayMs = 1000;
        private int batchSize = 50;
        private int lockSeconds = 60;
        private int maxRetries = 10;
        private int baseDelaySeconds = 5;

        /**
         * 判断当前对象是否处于特定状态。
         *
         * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
         */

        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 处理 set enabled 相关逻辑，并返回对应的执行结果。
         *
         * @param enabled 参数 enabled 对应的业务输入值
         */

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 统计值或数量结果
         */

        public long getPollDelayMs() {
            return pollDelayMs;
        }

        /**
         * 处理 set poll delay ms 相关逻辑，并返回对应的执行结果。
         *
         * @param pollDelayMs 参数 pollDelayMs 对应的业务输入值
         */

        public void setPollDelayMs(long pollDelayMs) {
            this.pollDelayMs = pollDelayMs;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 统计值或数量结果
         */

        public int getBatchSize() {
            return batchSize;
        }

        /**
         * 处理 set batch size 相关逻辑，并返回对应的执行结果。
         *
         * @param batchSize 参数 batchSize 对应的业务输入值
         */

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 统计值或数量结果
         */

        public int getLockSeconds() {
            return lockSeconds;
        }

        /**
         * 处理 set lock seconds 相关逻辑，并返回对应的执行结果。
         *
         * @param lockSeconds 参数 lockSeconds 对应的业务输入值
         */

        public void setLockSeconds(int lockSeconds) {
            this.lockSeconds = lockSeconds;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 统计值或数量结果
         */

        public int getMaxRetries() {
            return maxRetries;
        }

        /**
         * 处理 set max retries 相关逻辑，并返回对应的执行结果。
         *
         * @param maxRetries 参数 maxRetries 对应的业务输入值
         */

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 统计值或数量结果
         */

        public int getBaseDelaySeconds() {
            return baseDelaySeconds;
        }

        /**
         * 处理 set base delay seconds 相关逻辑，并返回对应的执行结果。
         *
         * @param baseDelaySeconds 参数 baseDelaySeconds 对应的业务输入值
         */

        public void setBaseDelaySeconds(int baseDelaySeconds) {
            this.baseDelaySeconds = baseDelaySeconds;
        }
    }

    /**
     * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
     */

    public static class Topology {

        /**
         * Declare workflow exchange / queues / bindings on startup.
         */
        private boolean enabled = false;

        private String searchIndexQueue = "cpw.workflow.search-index.refresh";
        private String readModelQueue = "cpw.workflow.read-model.sync";
        private String notificationQueue = "cpw.workflow.publish.notification";

        private final Consumer consumer = new Consumer();

        /**
         * 判断当前对象是否处于特定状态。
         *
         * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
         */

        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 处理 set enabled 相关逻辑，并返回对应的执行结果。
         *
         * @param enabled 参数 enabled 对应的业务输入值
         */

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 方法处理后的结果对象
         */

        public String getSearchIndexQueue() {
            return searchIndexQueue;
        }

        /**
         * 处理 set search index queue 相关逻辑，并返回对应的执行结果。
         *
         * @param searchIndexQueue 参数 searchIndexQueue 对应的业务输入值
         */

        public void setSearchIndexQueue(String searchIndexQueue) {
            this.searchIndexQueue = searchIndexQueue;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 方法处理后的结果对象
         */

        public String getReadModelQueue() {
            return readModelQueue;
        }

        /**
         * 处理 set read model queue 相关逻辑，并返回对应的执行结果。
         *
         * @param readModelQueue 参数 readModelQueue 对应的业务输入值
         */

        public void setReadModelQueue(String readModelQueue) {
            this.readModelQueue = readModelQueue;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 方法处理后的结果对象
         */

        public String getNotificationQueue() {
            return notificationQueue;
        }

        /**
         * 处理 set notification queue 相关逻辑，并返回对应的执行结果。
         *
         * @param notificationQueue 参数 notificationQueue 对应的业务输入值
         */

        public void setNotificationQueue(String notificationQueue) {
            this.notificationQueue = notificationQueue;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 方法处理后的结果对象
         */

        public Consumer getConsumer() {
            return consumer;
        }
    }

    /**
     * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
     */

    public static class Consumer {

        /**
         * Enable demo consumers. These listeners only log receipt and represent replaceable
         * downstream integrations.
         */
        private boolean enabled = false;

        /**
         * 判断当前对象是否处于特定状态。
         *
         * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
         */

        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 处理 set enabled 相关逻辑，并返回对应的执行结果。
         *
         * @param enabled 参数 enabled 对应的业务输入值
         */

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
