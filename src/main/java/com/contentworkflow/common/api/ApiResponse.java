package com.contentworkflow.common.api;

import java.time.LocalDateTime;

/**
 * ApiResponse 响应对象，用于封装接口返回数据。
 */

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        LocalDateTime timestamp
) {
    /**
     * 处理 ok 相关业务逻辑。
     * @param data 参数 data。
     * @return 方法执行后的结果对象。
     */

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "success", data, LocalDateTime.now());
    }

    /**
     * 处理 fail 相关业务逻辑。
     * @param code 参数 code。
     * @param message 参数 message。
     * @return 方法执行后的结果对象。
     */

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null, LocalDateTime.now());
    }
}
