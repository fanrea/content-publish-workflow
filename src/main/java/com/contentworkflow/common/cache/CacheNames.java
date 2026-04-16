package com.contentworkflow.common.cache;

/**
 * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
 */
public final class CacheNames {

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     */

    private CacheNames() {
    }

    public static final String DRAFT_DETAIL_BY_ID = "cpw:draft:detail:byId";
    public static final String DRAFT_DETAIL_BY_BIZ_NO = "cpw:draft:detail:byBizNo";
    public static final String DRAFT_LIST_LATEST = "cpw:draft:list:latest";
    public static final String DRAFT_STATUS_COUNT = "cpw:draft:count:byStatus";
    public static final String CONSUMED_WORKFLOW_MESSAGE = "cpw:mq:consumed:message";
    public static final String DEAD_OUTBOX_SCAN_MARKER = "cpw:ops:deadOutbox:scanMarker";
}
