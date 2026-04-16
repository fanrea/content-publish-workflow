package com.contentworkflow.workflow.interfaces.dto;

import jakarta.validation.constraints.Size;

/**
 * 接口层请求模型，用于封装客户端输入参数并承载校验约束。
 */

public record PublishRequest(
        @Size(max = 500) String remark,
        /**
         * Idempotency key for publish requests.
         *
         * <p>The caller should generate a stable UUID and pass it in the request body or through
         * the HTTP header {@code Idempotency-Key}.</p>
         */
        @Size(max = 128) String idempotencyKey
) {
    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param operator 当前操作人身份信息
     * @param remark 参数 remark 对应的业务输入值
     * @param idempotencyKey 参数 idempotencyKey 对应的业务输入值
     */
    public PublishRequest(String operator, String remark, String idempotencyKey) {
        this(remark, idempotencyKey);
    }
}
