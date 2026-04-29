package com.contentworkflow.document.application;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.document.domain.entity.DocumentMember;
import com.contentworkflow.document.domain.enums.DocumentMemberRole;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentMemberEntity;
import com.contentworkflow.document.infrastructure.persistence.mybatis.DocumentMemberMybatisMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档成员权限服务：负责角色查询与权限判定。
 */
@Service
public class DocumentPermissionService {

    private final DocumentMemberMybatisMapper memberMapper;

    public DocumentPermissionService(DocumentMemberMybatisMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    /**
     * 创建文档时初始化 owner。
     */
    @Transactional
    public void addOwnerMember(Long documentId, String ownerId, String ownerName) {
        memberMapper.upsertMember(
                documentId,
                normalizeMemberId(ownerId),
                normalizeMemberName(ownerName, ownerId),
                DocumentMemberRole.OWNER,
                LocalDateTime.now()
        );
    }

    /**
     * 查询文档成员列表。
     */
    @Transactional(readOnly = true)
    public List<DocumentMember> listMembers(Long documentId) {
        return memberMapper.selectByDocumentId(documentId).stream().map(this::toMember).toList();
    }

    /**
     * owner 管理成员角色。
     */
    @Transactional
    public DocumentMember upsertMember(Long documentId,
                                       String operatorId,
                                       String targetMemberId,
                                       String targetMemberName,
                                       DocumentMemberRole memberRole) {
        requireOwner(documentId, operatorId);

        String normalizedTargetId = normalizeMemberId(targetMemberId);
        DocumentMemberRole normalizedRole = memberRole == null ? DocumentMemberRole.VIEWER : memberRole;
        if (normalizedTargetId.equals(normalizeMemberId(operatorId)) && normalizedRole != DocumentMemberRole.OWNER) {
            throw forbidden("owner cannot downgrade self");
        }

        memberMapper.upsertMember(
                documentId,
                normalizedTargetId,
                normalizeMemberName(targetMemberName, normalizedTargetId),
                normalizedRole,
                LocalDateTime.now()
        );

        return toMember(requireMemberEntity(documentId, normalizedTargetId));
    }

    /**
     * 校验用户是文档成员（用于 JOIN/读协作态）。
     */
    @Transactional(readOnly = true)
    public DocumentMemberRole requireMember(Long documentId, String memberId) {
        return requireRole(documentId, memberId);
    }

    /**
     * 校验用户是否可编辑（owner/editor）。
     */
    @Transactional(readOnly = true)
    public DocumentMemberRole requireCanEdit(Long documentId, String memberId) {
        DocumentMemberRole role = requireRole(documentId, memberId);
        if (!role.canEdit()) {
            throw forbidden("viewer is not allowed to edit document");
        }
        return role;
    }

    /**
     * 校验用户是否 owner（恢复版本、成员管理）。
     */
    @Transactional(readOnly = true)
    public DocumentMemberRole requireOwner(Long documentId, String memberId) {
        DocumentMemberRole role = requireRole(documentId, memberId);
        if (!role.canRestore()) {
            throw forbidden("only owner can restore revisions");
        }
        return role;
    }

    private DocumentMemberRole requireRole(Long documentId, String memberId) {
        DocumentMemberEntity member = requireMemberEntity(documentId, normalizeMemberId(memberId));
        return member.getMemberRole();
    }

    private DocumentMemberEntity requireMemberEntity(Long documentId, String memberId) {
        return memberMapper.selectByDocumentIdAndMemberId(documentId, memberId)
                .orElseThrow(() -> forbidden("operator is not a document member"));
    }

    private String normalizeMemberId(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return "anonymous";
        }
        return memberId.trim();
    }

    private String normalizeMemberName(String memberName, String fallbackMemberId) {
        if (memberName == null || memberName.isBlank()) {
            return normalizeMemberId(fallbackMemberId);
        }
        return memberName.trim();
    }

    private DocumentMember toMember(DocumentMemberEntity entity) {
        return DocumentMember.builder()
                .id(entity.getId())
                .documentId(entity.getDocumentId())
                .memberId(entity.getMemberId())
                .memberName(entity.getMemberName())
                .memberRole(entity.getMemberRole())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private BusinessException forbidden(String message) {
        return new BusinessException("FORBIDDEN", message);
    }
}

