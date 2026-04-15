package com.contentworkflow.common.cache;

/**
 * Centralized Spring Cache names.
 */
public final class CacheNames {

    private CacheNames() {
    }

    public static final String DRAFT_DETAIL_BY_ID = "cpw:draft:detail:byId";
    public static final String DRAFT_DETAIL_BY_BIZ_NO = "cpw:draft:detail:byBizNo";
    public static final String DRAFT_LIST_LATEST = "cpw:draft:list:latest";
    public static final String DRAFT_STATUS_COUNT = "cpw:draft:count:byStatus";
    public static final String CONSUMED_WORKFLOW_MESSAGE = "cpw:mq:consumed:message";
    public static final String DEAD_OUTBOX_SCAN_MARKER = "cpw:ops:deadOutbox:scanMarker";
}
