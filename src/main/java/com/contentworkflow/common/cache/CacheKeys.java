package com.contentworkflow.common.cache;

/**
 * 缓存 Key 规范工具。
 *
 * <p>这里只定义业务维度的 key，不包含 cacheName 和全局前缀。
 * 例如：cacheName = cpw:draft:detail:byId，key = id:123。</p>
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    public static String draftId(Long draftId) {
        return "id:" + draftId;
    }

    public static String draftBizNo(String bizNo) {
        return "biz:" + bizNo;
    }

    public static String draftStatusCount(String status) {
        return "status:" + status;
    }

    public static String all() {
        return "all";
    }
}
