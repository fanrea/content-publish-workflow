package com.contentworkflow.common.messaging;

/**
 * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
 */
public final class WorkflowEventTypes {

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     */

    private WorkflowEventTypes() {
    }

    public static final String SEARCH_INDEX_REFRESH_REQUESTED = "SEARCH_INDEX_REFRESH_REQUESTED";
    public static final String DOWNSTREAM_READ_MODEL_SYNC_REQUESTED = "DOWNSTREAM_READ_MODEL_SYNC_REQUESTED";
    public static final String PUBLISH_NOTIFICATION_REQUESTED = "PUBLISH_NOTIFICATION_REQUESTED";

    public static final String CONTENT_PUBLISHED = "CONTENT_PUBLISHED";
    public static final String CONTENT_PUBLISH_FAILED = "CONTENT_PUBLISH_FAILED";
}
