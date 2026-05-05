package com.contentworkflow.document.application.storage;

import com.contentworkflow.document.infrastructure.persistence.entity.DocumentOperationEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemDocumentDeltaStoreTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void appendAndReplay_shouldPersistAcrossStoreRecreation() {
        FileSystemDocumentDeltaStore firstStore = new FileSystemDocumentDeltaStore(objectMapper, tempDir.toString());
        Long documentId = 101L;

        firstStore.appendIfAbsent(operation(documentId, 2, "session-a", 2L, "second"));
        firstStore.appendIfAbsent(operation(documentId, 1, "session-a", 1L, "first"));

        FileSystemDocumentDeltaStore secondStore = new FileSystemDocumentDeltaStore(objectMapper, tempDir.toString());
        List<DocumentOperationEntity> replay = secondStore.listByRevisionRange(documentId, 0, 10);

        assertThat(replay).hasSize(2);
        assertThat(replay).extracting(DocumentOperationEntity::getRevisionNo).containsExactly(1, 2);
        assertThat(secondStore.findByRevision(documentId, 2))
                .map(DocumentOperationEntity::getOpText)
                .contains("second");
        assertThat(secondStore.findBySessionSeq(documentId, "session-a", 1L))
                .map(DocumentOperationEntity::getRevisionNo)
                .contains(1);
    }

    @Test
    void appendIfAbsent_shouldBeIdempotentBySessionAndClientSeq() {
        Long documentId = 202L;
        FileSystemDocumentDeltaStore firstStore = new FileSystemDocumentDeltaStore(objectMapper, tempDir.toString());

        DocumentOperationEntity original = operation(documentId, 1, "session-b", 1L, "hello");
        DocumentDeltaStore.AppendResult appended = firstStore.appendIfAbsent(original);
        DocumentDeltaStore.AppendResult duplicateInSameStore = firstStore.appendIfAbsent(
                operation(documentId, 99, "session-b", 1L, "duplicate")
        );

        FileSystemDocumentDeltaStore secondStore = new FileSystemDocumentDeltaStore(objectMapper, tempDir.toString());
        DocumentDeltaStore.AppendResult duplicateAfterRestart = secondStore.appendIfAbsent(
                operation(documentId, 88, "session-b", 1L, "duplicate-after-restart")
        );

        List<DocumentOperationEntity> replay = secondStore.listByRevisionRange(documentId, 0, 20);
        assertThat(appended.duplicated()).isFalse();
        assertThat(duplicateInSameStore.duplicated()).isTrue();
        assertThat(duplicateAfterRestart.duplicated()).isTrue();
        assertThat(replay).hasSize(1);
        assertThat(replay.get(0).getRevisionNo()).isEqualTo(1);
        assertThat(replay.get(0).getOpText()).isEqualTo("hello");
    }

    private DocumentOperationEntity operation(
            Long documentId,
            Integer revisionNo,
            String sessionId,
            Long clientSeq,
            String opText) {
        DocumentOperationEntity operation = new DocumentOperationEntity();
        operation.setDocumentId(documentId);
        operation.setRevisionNo(revisionNo);
        operation.setSessionId(sessionId);
        operation.setClientSeq(clientSeq);
        operation.setOpText(opText);
        return operation;
    }
}
