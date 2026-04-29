package com.contentworkflow.document.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentRevisionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * DocumentRevisionMybatisMapper 映射接口，定义与持久化层的交互方法。
 */
@Mapper
public interface DocumentRevisionMybatisMapper extends BaseMapper<DocumentRevisionEntity> {

    /**
     * 查询某文档的历史版本。
     * @param documentId 参数 documentId。
     * @param limit 参数 limit。
     * @return 方法执行后的结果对象。
     */
    List<DocumentRevisionEntity> selectByDocumentIdOrderByRevisionDesc(@Param("documentId") Long documentId,
                                                                       @Param("limit") int limit);

    Optional<DocumentRevisionEntity> selectLatestSnapshotByRevision(@Param("documentId") Long documentId,
                                                                    @Param("revisionNo") Integer revisionNo);

    List<DocumentRevisionEntity> selectByRevisionRangeAsc(@Param("documentId") Long documentId,
                                                          @Param("fromRevisionExclusive") Integer fromRevisionExclusive,
                                                          @Param("toRevisionInclusive") Integer toRevisionInclusive,
                                                          @Param("limit") Integer limit);

    /**
     * 获取某文档指定版本。
     * @param documentId 参数 documentId。
     * @param revisionNo 参数 revisionNo。
     * @return 方法执行后的结果对象。
     */
    default Optional<DocumentRevisionEntity> selectByDocumentIdAndRevisionNo(Long documentId, Integer revisionNo) {
        LambdaQueryWrapper<DocumentRevisionEntity> query = new LambdaQueryWrapper<DocumentRevisionEntity>()
                .eq(DocumentRevisionEntity::getDocumentId, documentId)
                .eq(DocumentRevisionEntity::getRevisionNo, revisionNo)
                .last("limit 1");
        return Optional.ofNullable(selectOne(query));
    }
}
