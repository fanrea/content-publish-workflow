package com.contentworkflow.document.interfaces.dto;

import com.contentworkflow.document.domain.enums.DocumentMemberRole;
import jakarta.validation.constraints.NotNull;

/**
 * 设置文档成员角色请求。
 */
public record UpsertDocumentMemberRequest(
        String memberName,
        @NotNull(message = "memberRole must not be null")
        DocumentMemberRole memberRole
) {
}

