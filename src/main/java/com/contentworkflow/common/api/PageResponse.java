package com.contentworkflow.common.api;

import java.util.List;

/**
 * PageResponse 响应对象，用于封装接口返回数据。
 */
public record PageResponse<T>(
        List<T> items,
        long total,
        int pageNo,
        int pageSize,
        long totalPages
) {
}
