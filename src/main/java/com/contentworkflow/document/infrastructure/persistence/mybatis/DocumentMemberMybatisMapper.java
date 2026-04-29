package com.contentworkflow.document.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contentworkflow.document.domain.enums.DocumentMemberRole;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentMemberEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 文档成员持久层接口。
 */
@Mapper
public interface DocumentMemberMybatisMapper extends BaseMapper<DocumentMemberEntity> {

    /**
     * 查询文档全部成员。
     */
    List<DocumentMemberEntity> selectByDocumentId(@Param("documentId") Long documentId);

    /**
     * 新增或更新成员角色。
     */
    int upsertMember(@Param("documentId") Long documentId,
                     @Param("memberId") String memberId,
                     @Param("memberName") String memberName,
                     @Param("memberRole") DocumentMemberRole memberRole,
                     @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 按文档和成员 ID 查询。
     */
    default Optional<DocumentMemberEntity> selectByDocumentIdAndMemberId(Long documentId, String memberId) {
        LambdaQueryWrapper<DocumentMemberEntity> query = new LambdaQueryWrapper<DocumentMemberEntity>()
                .eq(DocumentMemberEntity::getDocumentId, documentId)
                .eq(DocumentMemberEntity::getMemberId, memberId)
                .last("limit 1");
        return Optional.ofNullable(selectOne(query));
    }

    /**
     * 按文档和成员 ID 集合批量查询。
     */
    default List<DocumentMemberEntity> selectByDocumentIdAndMemberIds(Long documentId, Collection<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<DocumentMemberEntity> query = new LambdaQueryWrapper<DocumentMemberEntity>()
                .eq(DocumentMemberEntity::getDocumentId, documentId)
                .in(DocumentMemberEntity::getMemberId, memberIds);
        return selectList(query);
    }
}
