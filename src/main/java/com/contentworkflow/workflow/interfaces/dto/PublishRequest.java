package com.contentworkflow.workflow.interfaces.dto;

import jakarta.validation.constraints.Size;

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
     * Backward-compatible constructor for older tests/callers that still pass operator first.
     */
    public PublishRequest(String operator, String remark, String idempotencyKey) {
        this(remark, idempotencyKey);
    }
}
