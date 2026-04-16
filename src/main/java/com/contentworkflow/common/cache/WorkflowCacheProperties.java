package com.contentworkflow.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 配置属性载体，用于绑定外部配置项并向运行时组件提供参数。
 */
@ConfigurationProperties(prefix = "workflow.cache")
public class WorkflowCacheProperties {

    /**
     * 全局 key 前缀。
     *
     * <p>建议用于区分环境或应用，避免多个服务共用同一个 Redis 时出现 key 冲突。</p>
     */
    private String keyPrefix = "cpw:";

    /** 草稿详情缓存 TTL。 */
    private Duration draftDetailTtl = Duration.ofSeconds(30);

    /**
     * 状态计数缓存 TTL。
     *
     * <p>建议更短一些，避免业务状态变更后统计长时间不一致。</p>
     */
    private Duration draftStatusCountTtl = Duration.ofSeconds(10);

    /**
     * 草稿列表缓存 TTL。
     *
     * <p>仅建议设置为很短的时间，主要用于 demo 或统计页。</p>
     */
    private Duration draftListTtl = Duration.ofSeconds(5);

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * 处理 set key prefix 相关逻辑，并返回对应的执行结果。
     *
     * @param keyPrefix 参数 keyPrefix 对应的业务输入值
     */

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public Duration getDraftDetailTtl() {
        return draftDetailTtl;
    }

    /**
     * 处理 set draft detail ttl 相关逻辑，并返回对应的执行结果。
     *
     * @param draftDetailTtl 参数 draftDetailTtl 对应的业务输入值
     */

    public void setDraftDetailTtl(Duration draftDetailTtl) {
        this.draftDetailTtl = draftDetailTtl;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public Duration getDraftStatusCountTtl() {
        return draftStatusCountTtl;
    }

    /**
     * 处理 set draft status count ttl 相关逻辑，并返回对应的执行结果。
     *
     * @param draftStatusCountTtl 参数 draftStatusCountTtl 对应的业务输入值
     */

    public void setDraftStatusCountTtl(Duration draftStatusCountTtl) {
        this.draftStatusCountTtl = draftStatusCountTtl;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public Duration getDraftListTtl() {
        return draftListTtl;
    }

    /**
     * 处理 set draft list ttl 相关逻辑，并返回对应的执行结果。
     *
     * @param draftListTtl 参数 draftListTtl 对应的业务输入值
     */

    public void setDraftListTtl(Duration draftListTtl) {
        this.draftListTtl = draftListTtl;
    }
}
