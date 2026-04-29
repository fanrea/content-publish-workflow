package com.contentworkflow.document.interfaces;

import com.contentworkflow.common.api.ApiResponse;
import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.document.application.DocumentCollaborationService;
import com.contentworkflow.document.application.DocumentCommentService;
import com.contentworkflow.document.application.DocumentPermissionService;
import com.contentworkflow.document.application.realtime.DocumentOperationService;
import com.contentworkflow.document.application.realtime.DocumentRealtimePushService;
import com.contentworkflow.document.domain.entity.CollaborativeDocument;
import com.contentworkflow.document.domain.entity.DocumentComment;
import com.contentworkflow.document.domain.entity.DocumentCommentReply;
import com.contentworkflow.document.domain.entity.DocumentMember;
import com.contentworkflow.document.domain.entity.DocumentOperation;
import com.contentworkflow.document.domain.entity.DocumentRevision;
import com.contentworkflow.document.domain.enums.DocumentChangeType;
import com.contentworkflow.document.interfaces.dto.CreateDocumentCommentReplyRequest;
import com.contentworkflow.document.interfaces.dto.CreateDocumentCommentRequest;
import com.contentworkflow.document.interfaces.dto.CreateDocumentRequest;
import com.contentworkflow.document.interfaces.dto.RestoreDocumentRevisionRequest;
import com.contentworkflow.document.interfaces.dto.UpdateDocumentRequest;
import com.contentworkflow.document.interfaces.dto.UpsertDocumentMemberRequest;
import com.contentworkflow.document.interfaces.vo.CollaborativeDocumentResponse;
import com.contentworkflow.document.interfaces.vo.DocumentCommentReplyResponse;
import com.contentworkflow.document.interfaces.vo.DocumentCommentResponse;
import com.contentworkflow.document.interfaces.vo.DocumentCommentSummaryResponse;
import com.contentworkflow.document.interfaces.vo.DocumentMemberResponse;
import com.contentworkflow.document.interfaces.vo.DocumentOperationResponse;
import com.contentworkflow.document.interfaces.vo.DocumentRevisionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 文档协作 HTTP 接口。
 */
@RestController
@Validated
@RequestMapping("/api/docs")
public class DocumentCollaborationController {

    private static final String EDITOR_ID_HEADER = "X-Editor-Id";
    private static final String EDITOR_NAME_HEADER = "X-Editor-Name";
    private static final String CLIENT_SESSION_ID_HEADER = "X-Client-Session-Id";
    private static final String CLIENT_SEQ_HEADER = "X-Client-Seq";

    private final DocumentCollaborationService documentService;
    private final DocumentPermissionService permissionService;
    private final DocumentOperationService operationService;
    private final DocumentCommentService commentService;
    private final DocumentRealtimePushService realtimePushService;

    public DocumentCollaborationController(DocumentCollaborationService documentService,
                                           DocumentPermissionService permissionService,
                                           DocumentOperationService operationService,
                                           DocumentCommentService commentService,
                                           DocumentRealtimePushService realtimePushService) {
        this.documentService = documentService;
        this.permissionService = permissionService;
        this.operationService = operationService;
        this.commentService = commentService;
        this.realtimePushService = realtimePushService;
    }

    @GetMapping
    public ApiResponse<List<CollaborativeDocumentResponse>> listDocuments(
            @RequestParam(value = "limit", defaultValue = "20")
            @Min(1) @Max(100) int limit,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                documentService.listDocumentsByMember(readEditorId(httpRequest), limit)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CollaborativeDocumentResponse> createDocument(@RequestBody @Valid CreateDocumentRequest request,
                                                                     HttpServletRequest httpRequest) {
        CollaborativeDocument created = documentService.createDocument(
                request.docNo(),
                request.title(),
                request.content(),
                readEditorId(httpRequest),
                readEditorName(httpRequest)
        );
        return ApiResponse.ok(toResponse(created));
    }

    @GetMapping("/{documentId}")
    public ApiResponse<CollaborativeDocumentResponse> getDocument(@PathVariable @Min(1) Long documentId,
                                                                  HttpServletRequest httpRequest) {
        permissionService.requireMember(documentId, readEditorId(httpRequest));
        return ApiResponse.ok(toResponse(documentService.getDocument(documentId)));
    }

    @PutMapping("/{documentId}")
    public ApiResponse<CollaborativeDocumentResponse> updateDocument(@PathVariable @Min(1) Long documentId,
                                                                     @RequestBody @Valid UpdateDocumentRequest request,
                                                                     HttpServletRequest httpRequest) {
        String editorId = readEditorId(httpRequest);
        String editorName = readEditorName(httpRequest);
        DocumentOperationService.ApplyResult result = operationService.applyFullReplaceOperation(
                documentId,
                request.baseRevision(),
                resolveClientSessionId(httpRequest, documentId, editorId),
                resolveClientSeq(httpRequest, documentId, editorId, request),
                editorId,
                editorName,
                request.title(),
                request.content(),
                request.changeSummary()
        );
        return ApiResponse.ok(toResponse(result.document()));
    }

    @GetMapping("/{documentId}/revisions")
    public ApiResponse<List<DocumentRevisionResponse>> listRevisions(
            @PathVariable @Min(1) Long documentId,
            @RequestParam(value = "limit", defaultValue = "50")
            @Min(1) @Max(200) int limit,
            HttpServletRequest httpRequest) {
        permissionService.requireMember(documentId, readEditorId(httpRequest));
        return ApiResponse.ok(documentService.listRevisions(documentId, limit).stream().map(this::toResponse).toList());
    }

    @PostMapping("/{documentId}/restore")
    public ApiResponse<CollaborativeDocumentResponse> restoreRevision(@PathVariable @Min(1) Long documentId,
                                                                      @RequestBody @Valid RestoreDocumentRevisionRequest request,
                                                                      HttpServletRequest httpRequest) {
        String editorId = readEditorId(httpRequest);
        String editorName = readEditorName(httpRequest);
        permissionService.requireOwner(documentId, editorId);

        DocumentRevision targetRevision = documentService.getRevision(documentId, request.targetRevision());
        DocumentOperationService.ApplyResult result = operationService.applyFullReplaceOperation(
                documentId,
                request.baseRevision(),
                resolveClientSessionId(httpRequest, documentId, editorId),
                resolveClientSeqForRestore(httpRequest, documentId, editorId, request),
                editorId,
                editorName,
                targetRevision.getTitle(),
                targetRevision.getContent(),
                resolveRestoreSummary(request),
                DocumentChangeType.RESTORE
        );
        return ApiResponse.ok(toResponse(result.document()));
    }

    /**
     * 查询增量操作（重连追赶）。
     */
    @GetMapping("/{documentId}/operations")
    public ApiResponse<List<DocumentOperationResponse>> listOperations(@PathVariable @Min(1) Long documentId,
                                                                       @RequestParam(value = "fromRevision", defaultValue = "0")
                                                                       @Min(0) int fromRevision,
                                                                       @RequestParam(value = "limit", defaultValue = "200")
                                                                       @Min(1) @Max(500) int limit,
                                                                       HttpServletRequest httpRequest) {
        permissionService.requireMember(documentId, readEditorId(httpRequest));
        return ApiResponse.ok(
                operationService.listOperationsSince(documentId, fromRevision, limit)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    /**
     * 查询评论统计。
     */
    @GetMapping("/{documentId}/comments/summary")
    public ApiResponse<DocumentCommentSummaryResponse> getCommentSummary(@PathVariable @Min(1) Long documentId,
                                                                         HttpServletRequest httpRequest) {
        DocumentCommentService.CommentSummary summary =
                commentService.getCommentSummary(documentId, readEditorId(httpRequest));
        return ApiResponse.ok(new DocumentCommentSummaryResponse(
                summary.totalCount(),
                summary.unresolvedCount(),
                summary.resolvedCount()
        ));
    }

    /**
     * 查询评论列表。
     */
    @GetMapping("/{documentId}/comments")
    public ApiResponse<List<DocumentCommentResponse>> listComments(@PathVariable @Min(1) Long documentId,
                                                                   @RequestParam(value = "limit", defaultValue = "50")
                                                                   @Min(1) @Max(200) int limit,
                                                                   HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                commentService.listComments(documentId, readEditorId(httpRequest), limit)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    /**
     * 添加评论。
     */
    @PostMapping("/{documentId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentCommentResponse> addComment(@PathVariable @Min(1) Long documentId,
                                                           @RequestBody @Valid CreateDocumentCommentRequest request,
                                                           HttpServletRequest httpRequest) {
        DocumentComment comment = commentService.addComment(
                documentId,
                readEditorId(httpRequest),
                readEditorName(httpRequest),
                request.baseRevision(),
                request.startOffset(),
                request.endOffset(),
                request.content()
        );
        realtimePushService.broadcastCommentCreated(documentId, comment);
        return ApiResponse.ok(toResponse(comment));
    }

    /**
     * 解决评论。
     */
    @PostMapping("/{documentId}/comments/{commentId}/resolve")
    public ApiResponse<DocumentCommentResponse> resolveComment(@PathVariable @Min(1) Long documentId,
                                                               @PathVariable @Min(1) Long commentId,
                                                               HttpServletRequest httpRequest) {
        DocumentComment comment = commentService.resolveComment(
                documentId,
                commentId,
                readEditorId(httpRequest),
                readEditorName(httpRequest)
        );
        realtimePushService.broadcastCommentResolved(documentId, comment);
        return ApiResponse.ok(toResponse(comment));
    }

    /**
     * 重开评论。
     */
    @PostMapping("/{documentId}/comments/{commentId}/reopen")
    public ApiResponse<DocumentCommentResponse> reopenComment(@PathVariable @Min(1) Long documentId,
                                                              @PathVariable @Min(1) Long commentId,
                                                              HttpServletRequest httpRequest) {
        DocumentComment comment = commentService.reopenComment(
                documentId,
                commentId,
                readEditorId(httpRequest),
                readEditorName(httpRequest)
        );
        realtimePushService.broadcastCommentReopened(documentId, comment);
        return ApiResponse.ok(toResponse(comment));
    }

    /**
     * 删除评论（软删除）。
     */
    @DeleteMapping("/{documentId}/comments/{commentId}")
    public ApiResponse<DocumentCommentResponse> deleteComment(@PathVariable @Min(1) Long documentId,
                                                              @PathVariable @Min(1) Long commentId,
                                                              HttpServletRequest httpRequest) {
        DocumentComment comment = commentService.deleteComment(
                documentId,
                commentId,
                readEditorId(httpRequest),
                readEditorName(httpRequest)
        );
        realtimePushService.broadcastCommentDeleted(documentId, comment);
        return ApiResponse.ok(toResponse(comment));
    }

    /**
     * 查询某条评论的回复列表。
     */
    @GetMapping("/{documentId}/comments/{commentId}/replies")
    public ApiResponse<List<DocumentCommentReplyResponse>> listReplies(@PathVariable @Min(1) Long documentId,
                                                                       @PathVariable @Min(1) Long commentId,
                                                                       @RequestParam(value = "limit", defaultValue = "100")
                                                                       @Min(1) @Max(500) int limit,
                                                                       HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                commentService.listReplies(documentId, commentId, readEditorId(httpRequest), limit)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    /**
     * 添加评论回复。
     */
    @PostMapping("/{documentId}/comments/{commentId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentCommentReplyResponse> addReply(@PathVariable @Min(1) Long documentId,
                                                              @PathVariable @Min(1) Long commentId,
                                                              @RequestBody @Valid CreateDocumentCommentReplyRequest request,
                                                              HttpServletRequest httpRequest) {
        DocumentCommentService.AddReplyResult result = commentService.addReply(
                documentId,
                commentId,
                request.replyToReplyId(),
                readEditorId(httpRequest),
                readEditorName(httpRequest),
                request.content()
        );
        if (result.reopenedComment() != null) {
            realtimePushService.broadcastCommentReopened(documentId, result.reopenedComment());
        }
        DocumentCommentReply reply = result.reply();
        realtimePushService.broadcastCommentReplyCreated(documentId, reply);
        return ApiResponse.ok(toResponse(reply));
    }

    /**
     * 查询成员列表（owner/editor/viewer）。
     */
    @GetMapping("/{documentId}/members")
    public ApiResponse<List<DocumentMemberResponse>> listMembers(@PathVariable @Min(1) Long documentId,
                                                                 HttpServletRequest httpRequest) {
        permissionService.requireMember(documentId, readEditorId(httpRequest));
        return ApiResponse.ok(documentService.listMembers(documentId).stream().map(this::toResponse).toList());
    }

    /**
     * owner 设置成员角色。
     */
    @PutMapping("/{documentId}/members/{memberId}")
    public ApiResponse<DocumentMemberResponse> upsertMember(@PathVariable @Min(1) Long documentId,
                                                            @PathVariable String memberId,
                                                            @RequestBody @Valid UpsertDocumentMemberRequest request,
                                                            HttpServletRequest httpRequest) {
        DocumentMember member = documentService.upsertMember(
                documentId,
                memberId,
                request.memberName(),
                request.memberRole(),
                readEditorId(httpRequest)
        );
        return ApiResponse.ok(toResponse(member));
    }

    private CollaborativeDocumentResponse toResponse(CollaborativeDocument document) {
        return new CollaborativeDocumentResponse(
                document.getId(),
                document.getDocNo(),
                document.getTitle(),
                document.getContent(),
                document.getLatestRevision(),
                document.getCreatedBy(),
                document.getUpdatedBy(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private DocumentRevisionResponse toResponse(DocumentRevision revision) {
        return new DocumentRevisionResponse(
                revision.getId(),
                revision.getDocumentId(),
                revision.getRevisionNo(),
                revision.getBaseRevision(),
                revision.getTitle(),
                revision.getContent(),
                revision.getEditorId(),
                revision.getEditorName(),
                revision.getChangeType(),
                revision.getChangeSummary(),
                revision.getCreatedAt()
        );
    }

    private DocumentOperationResponse toResponse(DocumentOperation operation) {
        return new DocumentOperationResponse(
                operation.getId(),
                operation.getDocumentId(),
                operation.getRevisionNo(),
                operation.getBaseRevision(),
                operation.getClientSeq(),
                operation.getOpType(),
                operation.getOpPosition(),
                operation.getOpLength(),
                operation.getOpText(),
                operation.getEditorId(),
                operation.getEditorName(),
                operation.getCreatedAt()
        );
    }

    private DocumentCommentResponse toResponse(DocumentComment comment) {
        return new DocumentCommentResponse(
                comment.getId(),
                comment.getDocumentId(),
                comment.getStartOffset(),
                comment.getEndOffset(),
                comment.getContent(),
                comment.getStatus(),
                comment.getCreatedById(),
                comment.getCreatedByName(),
                comment.getCreatedAt(),
                comment.getResolvedById(),
                comment.getResolvedByName(),
                comment.getResolvedAt()
        );
    }

    private DocumentCommentReplyResponse toResponse(DocumentCommentReply reply) {
        return new DocumentCommentReplyResponse(
                reply.getId(),
                reply.getDocumentId(),
                reply.getCommentId(),
                reply.getReplyToReplyId(),
                reply.getContent(),
                reply.getMentionMemberIds(),
                reply.getCreatedById(),
                reply.getCreatedByName(),
                reply.getCreatedAt()
        );
    }

    private DocumentMemberResponse toResponse(DocumentMember member) {
        return new DocumentMemberResponse(
                member.getId(),
                member.getDocumentId(),
                member.getMemberId(),
                member.getMemberName(),
                member.getMemberRole(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }

    private String resolveClientSessionId(HttpServletRequest request, Long documentId, String editorId) {
        String sessionId = request.getHeader(CLIENT_SESSION_ID_HEADER);
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId.trim();
        }
        return "http-" + documentId + "-" + editorId;
    }

    private Long resolveClientSeq(HttpServletRequest request,
                                  Long documentId,
                                  String editorId,
                                  UpdateDocumentRequest payload) {
        Long fromHeader = parseClientSeqHeader(request);
        if (fromHeader != null) {
            return fromHeader;
        }
        String fingerprint = documentId + "|"
                + editorId + "|"
                + payload.baseRevision() + "|"
                + payload.title() + "|"
                + payload.content() + "|"
                + (payload.changeSummary() == null ? "" : payload.changeSummary());
        return buildIdempotencySeqFromFingerprint(fingerprint);
    }

    private Long resolveClientSeqForRestore(HttpServletRequest request,
                                            Long documentId,
                                            String editorId,
                                            RestoreDocumentRevisionRequest payload) {
        Long fromHeader = parseClientSeqHeader(request);
        if (fromHeader != null) {
            return fromHeader;
        }
        String fingerprint = documentId + "|"
                + editorId + "|restore|"
                + payload.baseRevision() + "|"
                + payload.targetRevision() + "|"
                + (payload.changeSummary() == null ? "" : payload.changeSummary());
        return buildIdempotencySeqFromFingerprint(fingerprint);
    }

    private Long parseClientSeqHeader(HttpServletRequest request) {
        String headerValue = request.getHeader(CLIENT_SEQ_HEADER);
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            long clientSeq = Long.parseLong(headerValue.trim());
            if (clientSeq <= 0) {
                throw new NumberFormatException("clientSeq must be > 0");
            }
            return clientSeq;
        } catch (NumberFormatException ex) {
            throw new BusinessException("INVALID_ARGUMENT", "X-Client-Seq must be a positive long");
        }
    }

    private long buildIdempotencySeqFromFingerprint(String fingerprint) {
        long fallback = UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits()
                & Long.MAX_VALUE;
        return fallback == 0L ? 1L : fallback;
    }

    private String resolveRestoreSummary(RestoreDocumentRevisionRequest request) {
        if (request.changeSummary() != null && !request.changeSummary().isBlank()) {
            return request.changeSummary();
        }
        return "restore from revision " + request.targetRevision();
    }

    private String readEditorId(HttpServletRequest request) {
        String value = request.getHeader(EDITOR_ID_HEADER);
        return value == null || value.isBlank() ? "anonymous" : value.trim();
    }

    private String readEditorName(HttpServletRequest request) {
        String value = request.getHeader(EDITOR_NAME_HEADER);
        return value == null || value.isBlank() ? "anonymous" : value.trim();
    }
}
