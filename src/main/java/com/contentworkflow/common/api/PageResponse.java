package com.contentworkflow.common.api;

import java.util.List;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */
public record PageResponse<T>(
        List<T> items,
        long total,
        int pageNo,
        int pageSize,
        long totalPages
) {
}
