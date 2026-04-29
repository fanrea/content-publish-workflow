package com.contentworkflow.document.interfaces.ws;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WebSocket 出站事件模型。
 */
public record DocumentWsEvent(
        String type,
        String message,
        Long docId,
        Long serverSeq,
        Long clientSeq,
        Integer revision,
        Integer baseRevision,
        Integer latestRevision,
        String title,
        String content,
        String editorId,
        String editorName,
        DocumentWsOperation op,
        DocumentWsCursor cursor,
        DocumentWsComment comment,
        DocumentWsCommentReply reply,
        List<String> participants,
        LocalDateTime serverTime
) {
    /**
     * 初次加入时，下发文档快照。
     */
    public static DocumentWsEvent snapshot(Long docId, Integer revision, String title, String content) {
        return new DocumentWsEvent(
                "SNAPSHOT",
                null,
                docId,
                null,
                null,
                revision,
                null,
                revision,
                title,
                content,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                LocalDateTime.now()
        );
    }

    /**
     * 操作已被服务端确认并落库。
     */
    public static DocumentWsEvent ack(Long docId,
                                      Long serverSeq,
                                      Long clientSeq,
                                      Integer revision,
                                      Integer baseRevision,
                                      String editorId,
                                      String editorName,
                                      DocumentWsOperation op) {
        return new DocumentWsEvent(
                "ACK",
                null,
                docId,
                serverSeq,
                clientSeq,
                revision,
                baseRevision,
                revision,
                null,
                null,
                editorId,
                editorName,
                op,
                null,
                null,
                null,
                List.of(),
                LocalDateTime.now()
        );
    }

    /**
     * 操作已被入口层接收，等待异步处理。
     */
    public static DocumentWsEvent ackAcceptedByIngress(Long docId,
                                                       Long clientSeq,
                                                       Integer baseRevision,
                                                       String editorId,
                                                       String editorName,
                                                       DocumentWsOperation op) {
        return new DocumentWsEvent(
                "ACK",
                "accepted_by_ingress",
                docId,
                null,
                clientSeq,
                null,
                baseRevision,
                null,
                null,
                null,
                editorId,
                editorName,
                op,
                null,
                null,
                null,
                List.of(),
                LocalDateTime.now()
        );
    }

    /**
     * 广播操作应用事件，通知其他会话执行该操作。
     */
    public static DocumentWsEvent applied(Long docId,
                                          Long serverSeq,
                                          Integer revision,
                                          Integer baseRevision,
                                          String editorId,
                                          String editorName,
                                          DocumentWsOperation op) {
        return new DocumentWsEvent(
                "OP_APPLIED",
                null,
                docId,
                serverSeq,
                null,
                revision,
                baseRevision,
                revision,
                null,
                null,
                editorId,
                editorName,
                op,
                null,
                null,
                null,
                List.of(),
                LocalDateTime.now()
        );
    }

    /**
     * 广播光标/选区移动事件。
     */
    public static DocumentWsEvent cursorMoved(Long docId,
                                              String editorId,
                                              String editorName,
                                              DocumentWsCursor cursor) {
        return new DocumentWsEvent(
                "CURSOR_MOVED",
                null,
                docId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                editorId,
                editorName,
                null,
                cursor,
                null,
                null,
                List.of(),
                LocalDateTime.now()
        );
    }

    /**
     * 广播评论新增事件。
     */
    public static DocumentWsEvent commentCreated(Long docId, DocumentWsComment comment) {
        return new DocumentWsEvent(
                "COMMENT_CREATED",
                null,
                docId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                comment,
                null,
                List.of(),
                LocalDateTime.now()
        );
    }

    /**
     * 广播评论解决事件。
     */
    public static DocumentWsEvent commentResolved(Long docId, DocumentWsComment comment) {
        return new DocumentWsEvent(
                "COMMENT_RESOLVED",
                null,
                docId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                comment,
                null,
                List.of(),
                LocalDateTime.now()
        );
    }

    /**
     * 广播评论重开事件。
     */
    public static DocumentWsEvent commentReopened(Long docId, DocumentWsComment comment) {
        return new DocumentWsEvent(
                "COMMENT_REOPENED",
                null,
                docId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                comment,
                null,
                List.of(),
                LocalDateTime.now()
        );
    }

    /**
     * 广播评论删除事件。
     */
    public static DocumentWsEvent commentDeleted(Long docId, DocumentWsComment comment) {
        return new DocumentWsEvent(
                "COMMENT_DELETED",
                null,
                docId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                comment,
                null,
                List.of(),
                LocalDateTime.now()
        );
    }

    /**
     * 广播评论回复新增事件。
     */
    public static DocumentWsEvent commentReplyCreated(Long docId, DocumentWsCommentReply reply) {
        return new DocumentWsEvent(
                "COMMENT_REPLY_CREATED",
                null,
                docId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                reply,
                List.of(),
                LocalDateTime.now()
        );
    }

    /**
     * 增量同步结束事件。
     */
    public static DocumentWsEvent syncDone(Long docId,
                                           Integer fromRevision,
                                           Integer latestRevision,
                                           Integer replayedCount) {
        return new DocumentWsEvent(
                "SYNC_DONE",
                "replayedOps=" + replayedCount,
                docId,
                null,
                null,
                latestRevision,
                fromRevision,
                latestRevision,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                LocalDateTime.now()
        );
    }

    /**
     * 版本冲突，客户端需要先同步最新数据再重试。
     */
    public static DocumentWsEvent nackConflict(Long docId, Long clientSeq, Integer latestRevision, String title, String content) {
        return new DocumentWsEvent(
                "NACK_CONFLICT",
                "baseRevision is stale",
                docId,
                null,
                clientSeq,
                null,
                null,
                latestRevision,
                title,
                content,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                LocalDateTime.now()
        );
    }

    /**
     * 在线成员列表变更事件。
     */
    public static DocumentWsEvent presence(Long docId, List<String> participants, String message) {
        return new DocumentWsEvent(
                "PRESENCE",
                message,
                docId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                participants == null ? List.of() : participants,
                LocalDateTime.now()
        );
    }

    /**
     * 通用错误事件。
     */
    public static DocumentWsEvent error(Long docId, String message, Long clientSeq) {
        return new DocumentWsEvent(
                "ERROR",
                message,
                docId,
                null,
                clientSeq,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                LocalDateTime.now()
        );
    }
}
