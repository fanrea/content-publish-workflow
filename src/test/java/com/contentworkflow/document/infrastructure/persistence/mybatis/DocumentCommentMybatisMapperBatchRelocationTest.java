package com.contentworkflow.document.infrastructure.persistence.mybatis;

import com.contentworkflow.document.domain.enums.DocumentCommentStatus;
import com.contentworkflow.document.application.cache.DocumentCacheService;
import com.contentworkflow.document.application.engine.DocumentActorCollaborationEngine;
import com.contentworkflow.document.application.storage.DocumentDeltaStore;
import com.contentworkflow.document.application.storage.DocumentSnapshotStore;
import com.contentworkflow.document.application.realtime.DocumentRealtimeRedisIndex;
import com.contentworkflow.document.application.realtime.DocumentRealtimeRecentUpdateCache;
import com.contentworkflow.document.infrastructure.persistence.entity.DocumentCommentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false"
})
class DocumentCommentMybatisMapperBatchRelocationTest {

    @MockBean
    private DocumentCacheService documentCacheService;
    @MockBean
    private DocumentDeltaStore documentDeltaStore;
    @MockBean
    private DocumentSnapshotStore documentSnapshotStore;
    @MockBean
    private DocumentRealtimeRecentUpdateCache documentRealtimeRecentUpdateCache;
    @MockBean
    private DocumentRealtimeRedisIndex documentRealtimeRedisIndex;
    @MockBean
    private DocumentActorCollaborationEngine documentActorCollaborationEngine;

    @Autowired
    private DocumentCommentMybatisMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS collaborative_document_comment");
        jdbcTemplate.execute("""
                CREATE TABLE collaborative_document_comment (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    document_id BIGINT NOT NULL,
                    start_offset INT NOT NULL,
                    end_offset INT NOT NULL,
                    content VARCHAR(255) NULL,
                    status VARCHAR(32) NOT NULL,
                    created_by_id VARCHAR(64) NULL,
                    created_by_name VARCHAR(64) NULL,
                    created_at DATETIME NOT NULL,
                    resolved_by_id VARCHAR(64) NULL,
                    resolved_by_name VARCHAR(64) NULL,
                    resolved_at DATETIME NULL
                )
                """);
    }

    @Test
    void batchRelocateOpenAnchorsForInsert_shouldShiftBeforeAndExpandInside() {
        long docId = 1L;
        long before = insertComment(docId, 5, 8, DocumentCommentStatus.OPEN);
        long inside = insertComment(docId, 1, 6, DocumentCommentStatus.OPEN);
        long endBoundary = insertComment(docId, 1, 4, DocumentCommentStatus.OPEN);
        long resolved = insertComment(docId, 5, 8, DocumentCommentStatus.RESOLVED);
        long invalid = insertComment(docId, 9, 7, DocumentCommentStatus.OPEN);

        mapper.batchRelocateOpenAnchorsForInsert(docId, 4, 3);

        assertAnchor(before, 8, 11);
        assertAnchor(inside, 1, 9);
        assertAnchor(endBoundary, 1, 4);
        assertAnchor(resolved, 5, 8);
        assertAnchor(invalid, 9, 7);
    }

    @Test
    void batchRelocateOpenAnchorsForDelete_shouldHandleCrossAndCollapseInsideRange() {
        long docId = 2L;
        long cross = insertComment(docId, 2, 12, DocumentCommentStatus.OPEN);
        long inside = insertComment(docId, 6, 8, DocumentCommentStatus.OPEN);
        long after = insertComment(docId, 10, 14, DocumentCommentStatus.OPEN);
        long leftBoundary = insertComment(docId, 1, 4, DocumentCommentStatus.OPEN);
        long resolved = insertComment(docId, 10, 14, DocumentCommentStatus.RESOLVED);
        long invalid = insertComment(docId, 9, 7, DocumentCommentStatus.OPEN);

        mapper.batchRelocateOpenAnchorsForDelete(docId, 4, 5);

        assertAnchor(cross, 2, 7);
        assertAnchor(inside, 4, 4);
        assertAnchor(after, 5, 9);
        assertAnchor(leftBoundary, 1, 4);
        assertAnchor(resolved, 10, 14);
        assertAnchor(invalid, 9, 7);
    }

    @Test
    void batchRelocateOpenAnchorsForReplace_shouldApplyDeleteThenInsertSemantics() {
        long docId = 3L;
        long after = insertComment(docId, 10, 14, DocumentCommentStatus.OPEN);
        long cross = insertComment(docId, 2, 12, DocumentCommentStatus.OPEN);
        long atPosition = insertComment(docId, 4, 4, DocumentCommentStatus.OPEN);
        long endBoundary = insertComment(docId, 1, 4, DocumentCommentStatus.OPEN);

        mapper.batchRelocateOpenAnchorsForReplace(docId, 4, 5, 2);

        assertAnchor(after, 7, 11);
        assertAnchor(cross, 2, 9);
        assertAnchor(atPosition, 6, 6);
        assertAnchor(endBoundary, 1, 4);
    }

    private long insertComment(long documentId, int startOffset, int endOffset, DocumentCommentStatus status) {
        DocumentCommentEntity entity = new DocumentCommentEntity();
        entity.setDocumentId(documentId);
        entity.setStartOffset(startOffset);
        entity.setEndOffset(endOffset);
        entity.setContent("comment");
        entity.setStatus(status);
        entity.setCreatedById("u1");
        entity.setCreatedByName("U1");
        entity.setCreatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return entity.getId();
    }

    private void assertAnchor(long commentId, int expectedStart, int expectedEnd) {
        DocumentCommentEntity latest = mapper.selectById(commentId);
        assertThat(latest.getStartOffset()).isEqualTo(expectedStart);
        assertThat(latest.getEndOffset()).isEqualTo(expectedEnd);
    }
}
