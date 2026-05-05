package com.contentworkflow.document.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.contentworkflow.document.infrastructure.persistence.entity.CollaborativeDocumentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * CollaborativeDocumentMybatisMapper 映射接口，定义与持久化层的交互方法。
 */
@Mapper
public interface CollaborativeDocumentMybatisMapper extends BaseMapper<CollaborativeDocumentEntity> {

    /**
     * 查询最新更新时间靠前的数据。
     * @param limit 参数 limit。
     * @return 方法执行后的结果对象。
     */
    List<CollaborativeDocumentEntity> selectLatest(@Param("limit") int limit);

    List<CollaborativeDocumentEntity> selectLatestByMember(@Param("memberId") String memberId,
                                                           @Param("limit") int limit);

    /**
     * 带版本约束更新文档正文。
     * @param id 参数 id。
     * @param expectedVersion 参数 expectedVersion。
     * @param expectedRevision 参数 expectedRevision。
     * @param title 参数 title。
     * @param content 参数 content。
     * @param nextRevision 参数 nextRevision。
     * @param updatedBy 参数 updatedBy。
     * @param updatedAt 参数 updatedAt。
     * @return 方法执行后的结果对象。
     */
    @Deprecated
    int conditionalUpdate(@Param("id") Long id,
                          @Param("expectedVersion") Long expectedVersion,
                          @Param("expectedRevision") Integer expectedRevision,
                          @Param("title") String title,
                          @Param("content") String content,
                          @Param("nextRevision") Integer nextRevision,
                          @Param("updatedBy") String updatedBy,
                          @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Actor single-writer update path:
     * guard by expected revision only, and do not depend on lock_version match.
     */
    int actorSingleWriterUpdate(@Param("id") Long id,
                                @Param("expectedRevision") Integer expectedRevision,
                                @Param("title") String title,
                                @Param("content") String content,
                                @Param("nextRevision") Integer nextRevision,
                                @Param("updatedBy") String updatedBy,
                                @Param("updatedAt") LocalDateTime updatedAt);

    int actorSingleWriterUpdateMetadataOnly(@Param("id") Long id,
                                            @Param("expectedRevision") Integer expectedRevision,
                                            @Param("title") String title,
                                            @Param("nextRevision") Integer nextRevision,
                                            @Param("updatedBy") String updatedBy,
                                            @Param("updatedAt") LocalDateTime updatedAt);

    int updateSnapshotMetadata(@Param("id") Long id,
                               @Param("latestSnapshotRef") String latestSnapshotRef,
                               @Param("latestSnapshotRevision") Integer latestSnapshotRevision,
                               @Param("updatedBy") String updatedBy,
                               @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 获取 selectByDocNo 相关业务信息。
     * @param docNo 参数 docNo。
     * @return 方法执行后的结果对象。
     */
    default Optional<CollaborativeDocumentEntity> selectByDocNo(String docNo) {
        LambdaQueryWrapper<CollaborativeDocumentEntity> query = new LambdaQueryWrapper<CollaborativeDocumentEntity>()
                .eq(CollaborativeDocumentEntity::getDocNo, docNo)
                .last("limit 1");
        return Optional.ofNullable(selectOne(query));
    }
}
