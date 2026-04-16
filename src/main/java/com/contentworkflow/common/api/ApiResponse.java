package com.contentworkflow.common.api;

import java.time.LocalDateTime;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        LocalDateTime timestamp
) {
    /**
     * 处理 ok 相关逻辑，并返回对应的执行结果。
     *
     * @param data 参数 data 对应的业务输入值
     * @return 统一封装后的接口响应结果
     */

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "success", data, LocalDateTime.now());
    }

    /**
     * 处理 fail 相关逻辑，并返回对应的执行结果。
     *
     * @param code 业务错误码
     * @param message 提示信息
     * @return 统一封装后的接口响应结果
     */

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null, LocalDateTime.now());
    }
}
