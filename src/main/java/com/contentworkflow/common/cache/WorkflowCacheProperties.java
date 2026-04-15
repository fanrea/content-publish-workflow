package com.contentworkflow.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 缓存相关可配置项。
 *
 * <p>把 TTL、key 前缀等策略放到配置中，便于在不同环境下调优。</p>
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
}
