package com.contentworkflow.document.application;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.document.application.event.DocumentDomainEvent;
import com.contentworkflow.document.application.event.DocumentEventPublisher;
import com.contentworkflow.document.domain.entity.DocumentComment;
import com.contentworkflow.document.domain.entity.DocumentCommentReply;
import com.contentworkflow.document.domain.enums.DocumentCommentStatus;
import com.contentworkflow.document.domain.enums.DocumentMemberRole;
import com.contentworkflow.document.infrastructure.persistence.entity.CollaborativeDocumentEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentCommentEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentCommentReplyEntity;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentMemberEntity;
import com.contentworkflow.document.infrastructure.persistence.mybatis.CollaborativeDocumentMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentCommentMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentCommentReplyMybatisMapper;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentMemberMybatisMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档评论服务（评论、回复、统计）。
 */
@Service
public class DocumentCommentService {

    private static final int MAX_QUERY_LIMIT = 200;
    private static final int MAX_REPLY_QUERY_LIMIT = 500;
    private static final int MAX_MENTION_PER_REPLY = 20;
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_.-]{1,64})");

    private final DocumentCommentMybatisMapper commentMapper;
    private final DocumentCommentReplyMybatisMapper replyMapper;
    private final DocumentMemberMybatisMapper memberMapper;
    private final CollaborativeDocumentMybatisMapper documentMapper;
    private final DocumentPermissionService permissionService;
    private final DocumentEventPublisher eventPublisher;

    public DocumentCommentService(DocumentCommentMybatisMapper commentMapper,
                                  DocumentCommentReplyMybatisMapper replyMapper,
                                  DocumentMemberMybatisMapper memberMapper,
                                  CollaborativeDocumentMybatisMapper documentMapper,
                                  DocumentPermissionService permissionService,
                                  DocumentEventPublisher eventPublisher) {
        this.commentMapper = commentMapper;
        this.replyMapper = replyMapper;
        this.memberMapper = memberMapper;
        this.documentMapper = documentMapper;
        this.permissionService = permissionService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 查询文档评论列表。
     */
    @Transactional(readOnly = true)
    public List<DocumentComment> listComments(Long documentId, String operatorId, int limit) {
        Long normalizedDocId = normalizeDocumentId(documentId);
        permissionService.requireMember(normalizedDocId, normalizeOperatorId(operatorId));
        requireDocument(normalizedDocId);
        int normalizedLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, MAX_QUERY_LIMIT));
        return commentMapper.selectByDocumentId(normalizedDocId, normalizedLimit).stream()
                .map(this::toCommentDomain)
                .toList();
    }

    /**
     * 查询评论回复列表。
     */
    @Transactional(readOnly = true)
    public List<DocumentCommentReply> listReplies(Long documentId,
                                                  Long commentId,
                                                  String operatorId,
                                                  int limit) {
        Long normalizedDocId = normalizeDocumentId(documentId);
        Long normalizedCommentId = normalizeCommentId(commentId);
        permissionService.requireMember(normalizedDocId, normalizeOperatorId(operatorId));
        requireDocument(normalizedDocId);
        DocumentCommentEntity comment = requireComment(normalizedDocId, normalizedCommentId);
        requireCommentNotDeleted(comment);
        int normalizedLimit = Math.max(1, Math.min(limit <= 0 ? 100 : limit, MAX_REPLY_QUERY_LIMIT));
        return replyMapper.selectByCommentId(normalizedDocId, normalizedCommentId, normalizedLimit).stream()
                .map(this::toReplyDomain)
                .toList();
    }

    /**
     * 添加评论。
     */
    @Transactional
    public DocumentComment addComment(Long documentId,
                                      String operatorId,
                                      String operatorName,
                                      Integer baseRevision,
                                      Integer startOffset,
                                      Integer endOffset,
                                      String content) {
        Long normalizedDocId = normalizeDocumentId(documentId);
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        String normalizedOperatorName = normalizeOperatorName(operatorName);
        permissionService.requireMember(normalizedDocId, normalizedOperatorId);
        CollaborativeDocumentEntity document = requireDocument(normalizedDocId);
        int normalizedBaseRevision = normalizeCommentBaseRevision(baseRevision);
        if (!Objects.equals(document.getLatestRevision(), normalizedBaseRevision)) {
            throw new BusinessException(
                    "DOCUMENT_CONCURRENT_MODIFICATION",
                    "stale baseRevision for comment, documentId=" + normalizedDocId
                            + ", baseRevision=" + normalizedBaseRevision
                            + ", latestRevision=" + document.getLatestRevision()
            );
        }
        int normalizedStartOffset = normalizeStartOffset(startOffset);
        int normalizedEndOffset = normalizeEndOffset(normalizedStartOffset, endOffset);
        validateAnchorRangeInDocument(document, normalizedStartOffset, normalizedEndOffset);

        DocumentCommentEntity entity = new DocumentCommentEntity();
        entity.setDocumentId(normalizedDocId);
        entity.setStartOffset(normalizedStartOffset);
        entity.setEndOffset(normalizedEndOffset);
        entity.setContent(normalizeCommentContent(content));
        entity.setStatus(DocumentCommentStatus.OPEN);
        entity.setCreatedById(normalizedOperatorId);
        entity.setCreatedByName(normalizedOperatorName);
        entity.prepareForInsert();
        commentMapper.insert(entity);
        DocumentComment comment = toCommentDomain(entity);
        eventPublisher.publishAfterCommit(DocumentDomainEvent.of(
                "DOCUMENT_COMMENT_CREATED",
                normalizedDocId,
                null,
                normalizedOperatorId,
                normalizedOperatorName,
                Map.of(
                        "commentId", comment.getId(),
                        "baseRevision", normalizedBaseRevision,
                        "startOffset", comment.getStartOffset(),
                        "endOffset", comment.getEndOffset()
                )
        ));
        return comment;
    }

    /**
     * 添加回复（自动挂在某条评论下）。
     */
    @Transactional
    public AddReplyResult addReply(Long documentId,
                                   Long commentId,
                                   Long replyToReplyId,
                                   String operatorId,
                                   String operatorName,
                                   String content) {
        Long normalizedDocId = normalizeDocumentId(documentId);
        Long normalizedCommentId = normalizeCommentId(commentId);
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        String normalizedOperatorName = normalizeOperatorName(operatorName);
        permissionService.requireMember(normalizedDocId, normalizedOperatorId);
        requireDocument(normalizedDocId);
        DocumentCommentEntity comment = requireComment(normalizedDocId, normalizedCommentId);
        requireCommentNotDeleted(comment);
        Long normalizedReplyToReplyId = normalizeReplyToReplyId(replyToReplyId);
        String normalizedContent = normalizeReplyContent(content);
        validateReplyReference(normalizedDocId, normalizedCommentId, normalizedReplyToReplyId);
        List<String> mentionMemberIds = parseMentionMemberIds(normalizedContent);
        validateMentionMembers(normalizedDocId, mentionMemberIds);

        DocumentComment reopenedComment = null;
        if (comment.getStatus() == DocumentCommentStatus.RESOLVED) {
            commentMapper.reopenComment(normalizedDocId, normalizedCommentId);
            reopenedComment = toCommentDomain(requireComment(normalizedDocId, normalizedCommentId));
        }

        DocumentCommentReplyEntity entity = new DocumentCommentReplyEntity();
        entity.setDocumentId(normalizedDocId);
        entity.setCommentId(normalizedCommentId);
        entity.setReplyToReplyId(normalizedReplyToReplyId);
        entity.setContent(normalizedContent);
        entity.setMentionMemberIds(serializeMentionMemberIds(mentionMemberIds));
        entity.setCreatedById(normalizedOperatorId);
        entity.setCreatedByName(normalizedOperatorName);
        entity.prepareForInsert();
        replyMapper.insert(entity);
        DocumentCommentReply reply = toReplyDomain(entity);
        eventPublisher.publishAfterCommit(DocumentDomainEvent.of(
                "DOCUMENT_COMMENT_REPLY_CREATED",
                normalizedDocId,
                null,
                normalizedOperatorId,
                normalizedOperatorName,
                buildReplyPayload(normalizedCommentId, reply)
        ));
        if (reopenedComment != null) {
            eventPublisher.publishAfterCommit(DocumentDomainEvent.of(
                    "DOCUMENT_COMMENT_REOPENED",
                    normalizedDocId,
                    null,
                    normalizedOperatorId,
                    normalizedOperatorName,
                    Map.of(
                            "commentId", reopenedComment.getId(),
                            "status", reopenedComment.getStatus().name()
                    )
            ));
        }
        return new AddReplyResult(reply, reopenedComment);
    }

    /**
     * 解决评论：
     * 1) owner/editor 可解决任意评论
     * 2) 评论创建人可解决自己的评论
     */
    @Transactional
    public DocumentComment resolveComment(Long documentId,
                                          Long commentId,
                                          String operatorId,
                                          String operatorName) {
        Long normalizedDocId = normalizeDocumentId(documentId);
        Long normalizedCommentId = normalizeCommentId(commentId);
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        String normalizedOperatorName = normalizeOperatorName(operatorName);
        DocumentMemberRole role = permissionService.requireMember(normalizedDocId, normalizedOperatorId);
        requireDocument(normalizedDocId);

        DocumentCommentEntity comment = requireComment(normalizedDocId, normalizedCommentId);
        requireCommentNotDeleted(comment);

        boolean creatorCanResolve = Objects.equals(comment.getCreatedById(), normalizedOperatorId);
        boolean roleCanResolve = role.canEdit();
        if (!creatorCanResolve && !roleCanResolve) {
            throw new BusinessException("FORBIDDEN", "only owner/editor or comment creator can resolve");
        }

        if (comment.getStatus() == DocumentCommentStatus.RESOLVED) {
            return toCommentDomain(comment);
        }

        LocalDateTime resolvedAt = LocalDateTime.now();
        commentMapper.resolveComment(
                normalizedDocId,
                normalizedCommentId,
                normalizedOperatorId,
                normalizedOperatorName,
                resolvedAt
        );

        DocumentCommentEntity latest = requireComment(normalizedDocId, normalizedCommentId);
        DocumentComment latestComment = toCommentDomain(latest);
        eventPublisher.publishAfterCommit(DocumentDomainEvent.of(
                "DOCUMENT_COMMENT_RESOLVED",
                normalizedDocId,
                null,
                normalizedOperatorId,
                normalizedOperatorName,
                Map.of(
                        "commentId", latestComment.getId(),
                        "status", latestComment.getStatus().name()
                )
        ));
        return latestComment;
    }

    /**
     * 重新打开评论：
     * 1) owner/editor 可重开任意评论
     * 2) 评论创建人可重开自己的评论
     */
    @Transactional
    public DocumentComment reopenComment(Long documentId,
                                         Long commentId,
                                         String operatorId,
                                         String operatorName) {
        Long normalizedDocId = normalizeDocumentId(documentId);
        Long normalizedCommentId = normalizeCommentId(commentId);
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        DocumentMemberRole role = permissionService.requireMember(normalizedDocId, normalizedOperatorId);
        requireDocument(normalizedDocId);

        DocumentCommentEntity comment = requireComment(normalizedDocId, normalizedCommentId);
        requireCommentNotDeleted(comment);

        boolean creatorCanReopen = Objects.equals(comment.getCreatedById(), normalizedOperatorId);
        boolean roleCanReopen = role.canEdit();
        if (!creatorCanReopen && !roleCanReopen) {
            throw new BusinessException("FORBIDDEN", "only owner/editor or comment creator can reopen");
        }

        if (comment.getStatus() == DocumentCommentStatus.OPEN) {
            return toCommentDomain(comment);
        }

        commentMapper.reopenComment(normalizedDocId, normalizedCommentId);
        DocumentCommentEntity latest = requireComment(normalizedDocId, normalizedCommentId);
        DocumentComment latestComment = toCommentDomain(latest);
        eventPublisher.publishAfterCommit(DocumentDomainEvent.of(
                "DOCUMENT_COMMENT_REOPENED",
                normalizedDocId,
                null,
                normalizedOperatorId,
                normalizeOperatorName(operatorName),
                Map.of(
                        "commentId", latestComment.getId(),
                        "status", latestComment.getStatus().name()
                )
        ));
        return latestComment;
    }

    /**
     * 删除评论（软删除）：
     * 1) owner/editor 可删除任意评论
     * 2) 评论创建人可删除自己的评论
     */
    @Transactional
    public DocumentComment deleteComment(Long documentId,
                                         Long commentId,
                                         String operatorId,
                                         String operatorName) {
        Long normalizedDocId = normalizeDocumentId(documentId);
        Long normalizedCommentId = normalizeCommentId(commentId);
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        DocumentMemberRole role = permissionService.requireMember(normalizedDocId, normalizedOperatorId);
        requireDocument(normalizedDocId);

        DocumentCommentEntity comment = requireComment(normalizedDocId, normalizedCommentId);
        boolean creatorCanDelete = Objects.equals(comment.getCreatedById(), normalizedOperatorId);
        boolean roleCanDelete = role.canEdit();
        if (!creatorCanDelete && !roleCanDelete) {
            throw new BusinessException("FORBIDDEN", "only owner/editor or comment creator can delete");
        }

        if (comment.getStatus() == DocumentCommentStatus.DELETED) {
            return toCommentDomain(comment);
        }

        commentMapper.markCommentDeleted(normalizedDocId, normalizedCommentId);
        DocumentCommentEntity latest = requireComment(normalizedDocId, normalizedCommentId);
        DocumentComment latestComment = toCommentDomain(latest);
        eventPublisher.publishAfterCommit(DocumentDomainEvent.of(
                "DOCUMENT_COMMENT_DELETED",
                normalizedDocId,
                null,
                normalizedOperatorId,
                normalizeOperatorName(operatorName),
                Map.of(
                        "commentId", latestComment.getId(),
                        "status", latestComment.getStatus().name()
                )
        ));
        return latestComment;
    }

    /**
     * 评论聚合统计（用于列表角标和筛选）。
     */
    @Transactional(readOnly = true)
    public CommentSummary getCommentSummary(Long documentId, String operatorId) {
        Long normalizedDocId = normalizeDocumentId(documentId);
        permissionService.requireMember(normalizedDocId, normalizeOperatorId(operatorId));
        requireDocument(normalizedDocId);
        long totalCount = commentMapper.countByDocumentId(normalizedDocId);
        long unresolvedCount = commentMapper.countByDocumentIdAndStatus(normalizedDocId, DocumentCommentStatus.OPEN);
        return new CommentSummary(totalCount, unresolvedCount, totalCount - unresolvedCount);
    }

    private CollaborativeDocumentEntity requireDocument(Long documentId) {
        CollaborativeDocumentEntity entity = documentMapper.selectById(documentId);
        if (entity == null) {
            throw new BusinessException("DOCUMENT_NOT_FOUND", "document not found");
        }
        return entity;
    }

    private DocumentCommentEntity requireComment(Long documentId, Long commentId) {
        return commentMapper.selectByDocumentIdAndId(documentId, commentId)
                .orElseThrow(() -> new BusinessException("DOCUMENT_COMMENT_NOT_FOUND", "comment not found"));
    }

    private void requireCommentNotDeleted(DocumentCommentEntity comment) {
        if (comment.getStatus() == DocumentCommentStatus.DELETED) {
            throw new BusinessException("DOCUMENT_COMMENT_NOT_FOUND", "comment not found");
        }
    }

    private Long normalizeDocumentId(Long documentId) {
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "documentId must be > 0");
        }
        return documentId;
    }

    private Long normalizeCommentId(Long commentId) {
        if (commentId == null || commentId <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "commentId must be > 0");
        }
        return commentId;
    }

    private int normalizeCommentBaseRevision(Integer baseRevision) {
        if (baseRevision == null || baseRevision <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "baseRevision must be >= 1");
        }
        return baseRevision;
    }

    private Integer normalizeStartOffset(Integer startOffset) {
        if (startOffset == null || startOffset < 0) {
            throw new BusinessException("INVALID_ARGUMENT", "startOffset must be >= 0");
        }
        return startOffset;
    }

    private Integer normalizeEndOffset(Integer startOffset, Integer endOffset) {
        if (endOffset == null || endOffset < 0 || startOffset == null || endOffset < startOffset) {
            throw new BusinessException("INVALID_ARGUMENT", "endOffset must be >= startOffset");
        }
        return endOffset;
    }

    private void validateAnchorRangeInDocument(CollaborativeDocumentEntity document, Integer startOffset, Integer endOffset) {
        int contentLength = document.getContent() == null ? 0 : document.getContent().length();
        if (startOffset > contentLength || endOffset > contentLength) {
            throw new BusinessException(
                    "INVALID_ARGUMENT",
                    "comment anchor range is out of document content length"
            );
        }
    }

    private String normalizeCommentContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("INVALID_ARGUMENT", "comment content must not be blank");
        }
        String normalized = content.trim();
        if (normalized.length() > 2000) {
            throw new BusinessException("INVALID_ARGUMENT", "comment content is too long");
        }
        return normalized;
    }

    private String normalizeReplyContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("INVALID_ARGUMENT", "reply content must not be blank");
        }
        String normalized = content.trim();
        if (normalized.length() > 2000) {
            throw new BusinessException("INVALID_ARGUMENT", "reply content is too long");
        }
        return normalized;
    }

    private Long normalizeReplyToReplyId(Long replyToReplyId) {
        if (replyToReplyId == null) {
            return null;
        }
        if (replyToReplyId <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "replyToReplyId must be > 0");
        }
        return replyToReplyId;
    }

    /**
     * 校验回复引用目标是否合法，且必须在同一条评论下。
     */
    private void validateReplyReference(Long documentId, Long commentId, Long replyToReplyId) {
        if (replyToReplyId == null) {
            return;
        }
        DocumentCommentReplyEntity targetReply = replyMapper.selectByDocumentIdAndId(documentId, replyToReplyId)
                .orElseThrow(() -> new BusinessException("INVALID_ARGUMENT", "replyToReplyId does not exist"));
        if (!Objects.equals(targetReply.getCommentId(), commentId)) {
            throw new BusinessException("INVALID_ARGUMENT", "replyToReplyId must be under same comment");
        }
    }

    /**
     * 从回复正文中解析 @提及成员 ID（按出现顺序去重）。
     */
    private List<String> parseMentionMemberIds(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        Matcher matcher = MENTION_PATTERN.matcher(content);
        Set<String> mentions = new LinkedHashSet<>();
        while (matcher.find()) {
            if (mentions.size() >= MAX_MENTION_PER_REPLY) {
                throw new BusinessException("INVALID_ARGUMENT", "too many mentions in one reply");
            }
            mentions.add(matcher.group(1));
        }
        return List.copyOf(mentions);
    }

    /**
     * 校验被 @ 的成员都属于当前文档。
     */
    private void validateMentionMembers(Long documentId, List<String> mentionMemberIds) {
        if (mentionMemberIds == null || mentionMemberIds.isEmpty()) {
            return;
        }
        List<DocumentMemberEntity> members = memberMapper.selectByDocumentIdAndMemberIds(documentId, mentionMemberIds);
        Set<String> existingIds = members.stream()
                .map(DocumentMemberEntity::getMemberId)
                .collect(java.util.stream.Collectors.toSet());
        for (String mentionId : mentionMemberIds) {
            if (!existingIds.contains(mentionId)) {
                throw new BusinessException("INVALID_ARGUMENT", "mentioned member is not in document: " + mentionId);
            }
        }
    }

    private String serializeMentionMemberIds(List<String> mentionMemberIds) {
        if (mentionMemberIds == null || mentionMemberIds.isEmpty()) {
            return null;
        }
        return String.join(",", mentionMemberIds);
    }

    private List<String> deserializeMentionMemberIds(String rawMentionMemberIds) {
        if (rawMentionMemberIds == null || rawMentionMemberIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawMentionMemberIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private Map<String, Object> buildReplyPayload(Long commentId, DocumentCommentReply reply) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("commentId", commentId);
        payload.put("replyId", reply.getId());
        payload.put("replyToReplyId", reply.getReplyToReplyId());
        payload.put("mentionCount", reply.getMentionMemberIds().size());
        return payload;
    }

    private String normalizeOperatorId(String operatorId) {
        if (operatorId == null || operatorId.isBlank()) {
            return "anonymous";
        }
        return operatorId.trim();
    }

    private String normalizeOperatorName(String operatorName) {
        if (operatorName == null || operatorName.isBlank()) {
            return "anonymous";
        }
        return operatorName.trim();
    }

    private DocumentComment toCommentDomain(DocumentCommentEntity entity) {
        return DocumentComment.builder()
                .id(entity.getId())
                .documentId(entity.getDocumentId())
                .startOffset(entity.getStartOffset())
                .endOffset(entity.getEndOffset())
                .content(entity.getContent())
                .status(entity.getStatus())
                .createdById(entity.getCreatedById())
                .createdByName(entity.getCreatedByName())
                .createdAt(entity.getCreatedAt())
                .resolvedById(entity.getResolvedById())
                .resolvedByName(entity.getResolvedByName())
                .resolvedAt(entity.getResolvedAt())
                .build();
    }

    private DocumentCommentReply toReplyDomain(DocumentCommentReplyEntity entity) {
        return DocumentCommentReply.builder()
                .id(entity.getId())
                .documentId(entity.getDocumentId())
                .commentId(entity.getCommentId())
                .replyToReplyId(entity.getReplyToReplyId())
                .content(entity.getContent())
                .mentionMemberIds(deserializeMentionMemberIds(entity.getMentionMemberIds()))
                .createdById(entity.getCreatedById())
                .createdByName(entity.getCreatedByName())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * 评论聚合统计结果。
     */
    public record CommentSummary(
            Long totalCount,
            Long unresolvedCount,
            Long resolvedCount
    ) {
    }

    /**
     * 新增回复结果：
     * - reply: 本次新增的回复
     * - reopenedComment: 若本次回复触发了评论从 RESOLVED 自动重开，则返回重开后的评论；否则为 null
     */
    public record AddReplyResult(
            DocumentCommentReply reply,
            DocumentComment reopenedComment
    ) {
    }
}
