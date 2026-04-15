package com.contentworkflow.common.api;

import java.util.List;

/**
 * 分页返回模型（给前端列表页直接使用）。
 *
 * @param items      当前页数据
 * @param total      总条数
 * @param pageNo     页码（从 1 开始）
 * @param pageSize   每页大小
 * @param totalPages 总页数
 */
public record PageResponse<T>(
        List<T> items,
        long total,
        int pageNo,
        int pageSize,
        long totalPages
) {
}

