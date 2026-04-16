package com.contentworkflow.workflow.infrastructure.persistence.repository;

import com.contentworkflow.workflow.infrastructure.persistence.entity.PublishCommandJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 持久化仓储接口，负责定义数据库访问能力、查询条件以及相关的缓存协同策略。
 */

public interface PublishCommandJpaRepository extends JpaRepository<PublishCommandJpaEntity, Long> {

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param draftId 草稿唯一标识
     * @param commandType 参数 commandType 对应的业务输入值
     * @param idempotencyKey 参数 idempotencyKey 对应的业务输入值
     * @return 匹配到结果时返回对应对象，否则返回空的 Optional 容器
     */

    Optional<PublishCommandJpaEntity> findByDraftIdAndCommandTypeAndIdempotencyKey(Long draftId,
                                                                                    String commandType,
                                                                                    String idempotencyKey);

    /**
     * 按给定条件查找匹配的业务对象。
     *
     * @param draftId 草稿唯一标识
     * @return 符合条件的结果集合
     */

    List<PublishCommandJpaEntity> findByDraftIdOrderByCreatedAtDesc(Long draftId);
}
