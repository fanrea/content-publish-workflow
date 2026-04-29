package com.contentworkflow.document.interfaces.vo;

import com.contentworkflow.document.domain.enums.DocumentOpType;

import java.time.LocalDateTime;

/**
 * 文档操作响应对象（用于重连追赶）。
 */
public record DocumentOperationResponse(
        Long id,
        Long documentId,
        Integer revisionNo,
        Integer baseRevision,
        Long clientSeq,
        DocumentOpType opType,
        Integer opPosition,
        Integer opLength,
        String opText,
        String editorId,
        String editorName,
        LocalDateTime createdAt
) {
}

