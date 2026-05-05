package com.contentworkflow.document.application.ingress;

import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文档编辑入口命令：由网关接收后投递到异步处理链路。
 */
public record DocumentOperationIngressCommand(
        Long docId,
        Integer baseRevision,
        String sessionId,
        Long clientSeq,
        String editorId,
        String editorName,
        DocumentWsOperation op,
        LocalDateTime timestamp,
        String deltaBatchId,
        Long clientClock,
        Map<String, Long> baseVector
) {
    public DocumentOperationIngressCommand(
            Long docId,
            Integer baseRevision,
            String sessionId,
            Long clientSeq,
            String editorId,
            String editorName,
            DocumentWsOperation op,
            LocalDateTime timestamp) {
        this(docId, baseRevision, sessionId, clientSeq, editorId, editorName, op, timestamp, null, null, null);
    }
}
