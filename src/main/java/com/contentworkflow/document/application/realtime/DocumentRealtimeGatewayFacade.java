package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.domain.entity.DocumentOperation;

import java.util.List;

/**
 * Facade for gateway-side realtime decisions.
 */
public interface DocumentRealtimeGatewayFacade {

    JoinDecision prepareJoin(Long documentId, String editorId);

    void authorizeMember(Long documentId, String editorId);

    void authorizeCanEdit(Long documentId, String editorId);

    SyncDecision prepareSync(Long documentId, String editorId, int fromRevision, int limit);

    ConflictDecision resolveConflict(Long documentId);

    record JoinDecision(
            boolean snapshotAvailable,
            Integer revision,
            String title,
            String content,
            String instructionType,
            String instructionMessage
    ) {
        public static JoinDecision snapshot(Integer revision, String title, String content) {
            return new JoinDecision(true, revision, title, content, null, null);
        }

        public static JoinDecision instruction(String instructionType, String instructionMessage) {
            return new JoinDecision(false, null, null, null, instructionType, instructionMessage);
        }

        public boolean requiresInstruction() {
            return instructionType != null && !instructionType.isBlank();
        }
    }

    record SyncDecision(
            List<DocumentOperation> operations,
            int latestRevision,
            String instructionType,
            String instructionMessage
    ) {
        public SyncDecision {
            operations = operations == null ? List.of() : List.copyOf(operations);
        }

        public static SyncDecision replay(List<DocumentOperation> operations, int latestRevision) {
            return new SyncDecision(operations, latestRevision, null, null);
        }

        public static SyncDecision instruction(int latestRevision, String instructionType, String instructionMessage) {
            return new SyncDecision(List.of(), latestRevision, instructionType, instructionMessage);
        }

        public boolean requiresInstruction() {
            return instructionType != null && !instructionType.isBlank();
        }
    }

    record ConflictDecision(
            boolean snapshotAvailable,
            Integer latestRevision,
            String title,
            String content,
            String instructionType,
            String instructionMessage
    ) {
        public static ConflictDecision snapshot(Integer latestRevision, String title, String content) {
            return new ConflictDecision(true, latestRevision, title, content, null, null);
        }

        public static ConflictDecision instruction(String instructionType, String instructionMessage) {
            return new ConflictDecision(false, null, null, null, instructionType, instructionMessage);
        }

        public boolean requiresInstruction() {
            return instructionType != null && !instructionType.isBlank();
        }
    }
}
