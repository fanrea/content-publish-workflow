package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import com.contentworkflow.workflow.infrastructure.persistence.entity.ContentDraftEntity;
import com.contentworkflow.workflow.interfaces.dto.DraftQueryRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ContentDraftMybatisMapper {

    Optional<ContentDraftEntity> selectById(Long id);

    Optional<ContentDraftEntity> selectByBizNo(String bizNo);

    List<ContentDraftEntity> selectAllOrderByUpdatedAtDesc();

    List<ContentDraftEntity> selectPage(@Param("request") DraftQueryRequest request,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit,
                                           @Param("sortColumn") String sortColumn,
                                           @Param("sortDirection") String sortDirection);

    long countPage(@Param("request") DraftQueryRequest request);

    List<DraftStatusCountRow> countByStatus(@Param("request") DraftQueryRequest request);

    int insert(ContentDraftEntity entity);

    int conditionalUpdate(@Param("id") Long id,
                          @Param("expectedVersion") Long expectedVersion,
                          @Param("expectedStatuses") Collection<WorkflowStatus> expectedStatuses,
                          @Param("bizNo") String bizNo,
                          @Param("title") String title,
                          @Param("summary") String summary,
                          @Param("body") String body,
                          @Param("draftVersion") Integer draftVersion,
                          @Param("publishedVersion") Integer publishedVersion,
                          @Param("workflowStatus") WorkflowStatus workflowStatus,
                          @Param("currentSnapshotId") Long currentSnapshotId,
                          @Param("lastReviewComment") String lastReviewComment,
                          @Param("updatedAt") LocalDateTime updatedAt);
}
