package com.contentworkflow.document.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentCommentReplyEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

/**
 * 评论回复持久层接口。
 */
@Mapper
public interface DocumentCommentReplyMybatisMapper extends BaseMapper<DocumentCommentReplyEntity> {

    /**
     * 查询某条评论的回复（按时间正序）。
     */
    default List<DocumentCommentReplyEntity> selectByCommentId(Long documentId, Long commentId, int limit) {
        LambdaQueryWrapper<DocumentCommentReplyEntity> query = new LambdaQueryWrapper<DocumentCommentReplyEntity>()
                .eq(DocumentCommentReplyEntity::getDocumentId, documentId)
                .eq(DocumentCommentReplyEntity::getCommentId, commentId)
                .orderByAsc(DocumentCommentReplyEntity::getId)
                .last("limit " + limit);
        return selectList(query);
    }

    /**
     * 按文档和回复 ID 查询。
     */
    default Optional<DocumentCommentReplyEntity> selectByDocumentIdAndId(Long documentId, Long replyId) {
        LambdaQueryWrapper<DocumentCommentReplyEntity> query = new LambdaQueryWrapper<DocumentCommentReplyEntity>()
                .eq(DocumentCommentReplyEntity::getDocumentId, documentId)
                .eq(DocumentCommentReplyEntity::getId, replyId)
                .last("limit 1");
        return Optional.ofNullable(selectOne(query));
    }
}
