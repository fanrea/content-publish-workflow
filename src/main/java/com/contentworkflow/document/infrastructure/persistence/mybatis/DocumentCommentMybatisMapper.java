package com.contentworkflow.document.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contentworkflow.document.domain.enums.DocumentCommentStatus;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentCommentEntity;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文档评论持久层接口。
 */
@Mapper
public interface DocumentCommentMybatisMapper extends BaseMapper<DocumentCommentEntity> {

    /**
     * 按文档查询评论列表（新评论优先）。
     */
    default List<DocumentCommentEntity> selectByDocumentId(Long documentId, int limit) {
        LambdaQueryWrapper<DocumentCommentEntity> query = new LambdaQueryWrapper<DocumentCommentEntity>()
                .eq(DocumentCommentEntity::getDocumentId, documentId)
                .ne(DocumentCommentEntity::getStatus, DocumentCommentStatus.DELETED)
                .orderByDesc(DocumentCommentEntity::getId)
                .last("limit " + limit);
        return selectList(query);
    }

    /**
     * 按文档和评论 ID 查询。
     */
    default Optional<DocumentCommentEntity> selectByDocumentIdAndId(Long documentId, Long commentId) {
        LambdaQueryWrapper<DocumentCommentEntity> query = new LambdaQueryWrapper<DocumentCommentEntity>()
                .eq(DocumentCommentEntity::getDocumentId, documentId)
                .eq(DocumentCommentEntity::getId, commentId)
                .last("limit 1");
        return Optional.ofNullable(selectOne(query));
    }

    /**
     * 查询文档下仍处于 OPEN 的评论（用于锚点迁移）。
     */
    default List<DocumentCommentEntity> selectOpenByDocumentId(Long documentId) {
        LambdaQueryWrapper<DocumentCommentEntity> query = new LambdaQueryWrapper<DocumentCommentEntity>()
                .eq(DocumentCommentEntity::getDocumentId, documentId)
                .eq(DocumentCommentEntity::getStatus, DocumentCommentStatus.OPEN);
        return selectList(query);
    }

    /**
     * 查询可能受编辑影响的 OPEN 评论，避免每次编辑都全量扫描评论集合。
     */
    default List<DocumentCommentEntity> selectOpenByDocumentIdFromOffset(Long documentId, Integer operationPosition) {
        int normalizedPosition = operationPosition == null ? 0 : Math.max(0, operationPosition);
        LambdaQueryWrapper<DocumentCommentEntity> query = new LambdaQueryWrapper<DocumentCommentEntity>()
                .eq(DocumentCommentEntity::getDocumentId, documentId)
                .eq(DocumentCommentEntity::getStatus, DocumentCommentStatus.OPEN)
                .ge(DocumentCommentEntity::getEndOffset, normalizedPosition);
        return selectList(query);
    }

    /**
     * 统计文档评论总数。
     */
    default long countByDocumentId(Long documentId) {
        LambdaQueryWrapper<DocumentCommentEntity> query = new LambdaQueryWrapper<DocumentCommentEntity>()
                .eq(DocumentCommentEntity::getDocumentId, documentId)
                .ne(DocumentCommentEntity::getStatus, DocumentCommentStatus.DELETED);
        return selectCount(query);
    }

    /**
     * 按状态统计文档评论数量。
     */
    default long countByDocumentIdAndStatus(Long documentId, DocumentCommentStatus status) {
        LambdaQueryWrapper<DocumentCommentEntity> query = new LambdaQueryWrapper<DocumentCommentEntity>()
                .eq(DocumentCommentEntity::getDocumentId, documentId)
                .eq(DocumentCommentEntity::getStatus, status);
        return selectCount(query);
    }

    /**
     * 标记评论为已解决（仅 OPEN -> RESOLVED）。
     */
    default int resolveComment(Long documentId,
                               Long commentId,
                               String resolvedById,
                               String resolvedByName,
                               LocalDateTime resolvedAt) {
        LambdaUpdateWrapper<DocumentCommentEntity> condition = new LambdaUpdateWrapper<DocumentCommentEntity>()
                .eq(DocumentCommentEntity::getDocumentId, documentId)
                .eq(DocumentCommentEntity::getId, commentId)
                .eq(DocumentCommentEntity::getStatus, DocumentCommentStatus.OPEN)
                .set(DocumentCommentEntity::getStatus, DocumentCommentStatus.RESOLVED)
                .set(DocumentCommentEntity::getResolvedById, resolvedById)
                .set(DocumentCommentEntity::getResolvedByName, resolvedByName)
                .set(DocumentCommentEntity::getResolvedAt, resolvedAt);
        return update(null, condition);
    }

    /**
     * 重新打开评论（仅 RESOLVED -> OPEN）。
     */
    default int reopenComment(Long documentId, Long commentId) {
        LambdaUpdateWrapper<DocumentCommentEntity> condition = new LambdaUpdateWrapper<DocumentCommentEntity>()
                .eq(DocumentCommentEntity::getDocumentId, documentId)
                .eq(DocumentCommentEntity::getId, commentId)
                .eq(DocumentCommentEntity::getStatus, DocumentCommentStatus.RESOLVED)
                .set(DocumentCommentEntity::getStatus, DocumentCommentStatus.OPEN)
                .set(DocumentCommentEntity::getResolvedById, null)
                .set(DocumentCommentEntity::getResolvedByName, null)
                .set(DocumentCommentEntity::getResolvedAt, null);
        return update(null, condition);
    }

    /**
     * 软删除评论（非 DELETED -> DELETED）。
     */
    default int markCommentDeleted(Long documentId, Long commentId) {
        LambdaUpdateWrapper<DocumentCommentEntity> condition = new LambdaUpdateWrapper<DocumentCommentEntity>()
                .eq(DocumentCommentEntity::getDocumentId, documentId)
                .eq(DocumentCommentEntity::getId, commentId)
                .ne(DocumentCommentEntity::getStatus, DocumentCommentStatus.DELETED)
                .set(DocumentCommentEntity::getStatus, DocumentCommentStatus.DELETED);
        return update(null, condition);
    }

    /**
     * 更新评论锚点区间（start/end）。
     */
    default int updateAnchorRange(Long documentId, Long commentId, Integer startOffset, Integer endOffset) {
        LambdaUpdateWrapper<DocumentCommentEntity> condition = new LambdaUpdateWrapper<DocumentCommentEntity>()
                .eq(DocumentCommentEntity::getDocumentId, documentId)
                .eq(DocumentCommentEntity::getId, commentId)
                .set(DocumentCommentEntity::getStartOffset, startOffset)
                .set(DocumentCommentEntity::getEndOffset, endOffset);
        return update(null, condition);
    }

    /**
     * 批量迁移 OPEN 评论锚点（INSERT）。
     * 语义与 OT-lite 保持一致：
     * 1) pos <= start: start/end 同步右移；
     * 2) start < pos < end: 仅 end 右移；
     * 3) 其余不变。
     */
    default int batchRelocateOpenAnchorsForInsert(Long documentId, Integer operationPosition, Integer insertedLength) {
        int pos = operationPosition == null ? 0 : Math.max(0, operationPosition);
        int delta = insertedLength == null ? 0 : Math.max(0, insertedLength);
        if (delta <= 0) {
            return 0;
        }

        String startExpr = "CASE WHEN start_offset >= " + pos
                + " THEN start_offset + " + delta
                + " ELSE start_offset END";
        String endExpr = "CASE WHEN start_offset >= " + pos
                + " THEN end_offset + " + delta
                + " WHEN start_offset < " + pos + " AND end_offset > " + pos
                + " THEN end_offset + " + delta
                + " ELSE end_offset END";

        LambdaUpdateWrapper<DocumentCommentEntity> condition = new LambdaUpdateWrapper<DocumentCommentEntity>()
                .eq(DocumentCommentEntity::getDocumentId, documentId)
                .eq(DocumentCommentEntity::getStatus, DocumentCommentStatus.OPEN)
                .apply("end_offset >= start_offset")
                .and(wrapper -> wrapper
                        .gt(DocumentCommentEntity::getEndOffset, pos)
                        .or()
                        .ge(DocumentCommentEntity::getStartOffset, pos))
                .setSql("start_offset = " + startExpr + ", end_offset = " + endExpr);
        return update(null, condition);
    }

    /**
     * 批量迁移 OPEN 评论锚点（DELETE）。
     * 语义与 OT-lite 保持一致：点位按删除区间投影，最终 end >= start。
     */
    default int batchRelocateOpenAnchorsForDelete(Long documentId, Integer operationPosition, Integer deletedLength) {
        int deleteStart = operationPosition == null ? 0 : Math.max(0, operationPosition);
        int delta = deletedLength == null ? 0 : Math.max(0, deletedLength);
        if (delta <= 0) {
            return 0;
        }
        int deleteEnd = (int) Math.min((long) Integer.MAX_VALUE, (long) deleteStart + delta);

        String startExpr = transformPointByDeleteSql("start_offset", deleteStart, deleteEnd, delta);
        String endExpr = transformPointByDeleteSql("end_offset", deleteStart, deleteEnd, delta);
        String normalizedEndExpr = "GREATEST(" + startExpr + ", " + endExpr + ")";

        LambdaUpdateWrapper<DocumentCommentEntity> condition = new LambdaUpdateWrapper<DocumentCommentEntity>()
                .eq(DocumentCommentEntity::getDocumentId, documentId)
                .eq(DocumentCommentEntity::getStatus, DocumentCommentStatus.OPEN)
                .apply("end_offset >= start_offset")
                .gt(DocumentCommentEntity::getEndOffset, deleteStart)
                .setSql("start_offset = " + startExpr + ", end_offset = " + normalizedEndExpr);
        return update(null, condition);
    }

    /**
     * 批量迁移 OPEN 评论锚点（REPLACE = DELETE + INSERT）。
     */
    default int batchRelocateOpenAnchorsForReplace(Long documentId,
                                                   Integer operationPosition,
                                                   Integer replacedLength,
                                                   Integer insertedLength) {
        int affected = 0;
        affected += batchRelocateOpenAnchorsForDelete(documentId, operationPosition, replacedLength);
        affected += batchRelocateOpenAnchorsForInsert(documentId, operationPosition, insertedLength);
        return affected;
    }

    private static String transformPointByDeleteSql(String column, int deleteStart, int deleteEnd, int deletedLen) {
        return "CASE "
                + "WHEN " + column + " >= " + deleteEnd + " THEN " + column + " - " + deletedLen + " "
                + "WHEN " + column + " <= " + deleteStart + " THEN " + column + " "
                + "ELSE " + deleteStart + " END";
    }
}
