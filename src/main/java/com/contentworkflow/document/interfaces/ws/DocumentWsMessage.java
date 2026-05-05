package com.contentworkflow.document.interfaces.ws;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * WebSocket 入站消息模型（自定义轻量协议）。
 */
@Data
public class DocumentWsMessage {
    /**
     * 消息类型：JOIN / LEAVE / EDIT_OP / SYNC_OPS / CURSOR_MOVE。
     */
    private String type;
    /**
     * 文档 ID。
     */
    private Long docId;
    /**
     * 客户端当前基线 revision：
     * - EDIT_OP：作为并发冲突检测基线
     * - SYNC_OPS：表示从该 revision 之后开始追操作
     */
    private Integer baseRevision;
    /**
     * 客户端会话内递增序号（用于幂等去重）。
     */
    private Long clientSeq;
    private String clientSessionId;
    private String deltaBatchId;
    private Long clientClock;
    private Map<String, Long> baseVector;
    /**
     * 编辑人标识。
     */
    private String editorId;
    /**
     * 编辑人名称。
     */
    private String editorName;
    /**
     * 增量同步上限（仅 SYNC_OPS 生效，不传默认 200）。
     */
    private Integer syncLimit;
    /**
     * 具体编辑操作（仅 EDIT_OP 需要）。
     */
    private DocumentWsOperation op;
    /**
     * 光标/选区信息（仅 CURSOR_MOVE 需要）。
     */
    private DocumentWsCursor cursor;
    /**
     * 客户端上报时间（可选），未传则由服务端使用接收时间。
     */
    private LocalDateTime timestamp;
}
