package com.contentworkflow.common.cache;

/**
 * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
 */
public final class CacheKeys {

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     */

    private CacheKeys() {
    }

    /**
     * 处理 draft id 相关逻辑，并返回对应的执行结果。
     *
     * @param draftId 草稿唯一标识
     * @return 方法处理后的结果对象
     */

    public static String draftId(Long draftId) {
        return "id:" + draftId;
    }

    /**
     * 处理 draft biz no 相关逻辑，并返回对应的执行结果。
     *
     * @param bizNo 业务编号
     * @return 方法处理后的结果对象
     */

    public static String draftBizNo(String bizNo) {
        return "biz:" + bizNo;
    }

    /**
     * 处理 draft status count 相关逻辑，并返回对应的执行结果。
     *
     * @param status 状态值
     * @return 方法处理后的结果对象
     */

    public static String draftStatusCount(String status) {
        return "status:" + status;
    }

    /**
     * 处理 all 相关逻辑，并返回对应的执行结果。
     *
     * @return 方法处理后的结果对象
     */

    public static String all() {
        return "all";
    }
}
