package com.contentworkflow.document.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 文档操作持久层接口。
 */
@Mapper
public interface DocumentOperationMybatisMapper extends BaseMapper<DocumentOperationEntity> {

    /**
     * 按 (documentId, sessionId, clientSeq) 查找已处理操作，用于幂等去重。
     */
    default Optional<DocumentOperationEntity> selectBySessionSeq(Long documentId, String sessionId, Long clientSeq) {
        LambdaQueryWrapper<DocumentOperationEntity> query = new LambdaQueryWrapper<DocumentOperationEntity>()
                .eq(DocumentOperationEntity::getDocumentId, documentId)
                .eq(DocumentOperationEntity::getSessionId, sessionId)
                .eq(DocumentOperationEntity::getClientSeq, clientSeq)
                .last("limit 1");
        return Optional.ofNullable(selectOne(query));
    }

    /**
     * 按文档和版本号查询操作记录。
     */
    Optional<DocumentOperationEntity> selectByRevision(@Param("documentId") Long documentId,
                                                       @Param("revisionNo") Integer revisionNo);

    /**
     * 按版本范围查询操作（用于重连追赶）。
     */
    List<DocumentOperationEntity> selectByRevisionRange(@Param("documentId") Long documentId,
                                                        @Param("fromRevisionExclusive") Integer fromRevisionExclusive,
                                                        @Param("limit") Integer limit);
}

