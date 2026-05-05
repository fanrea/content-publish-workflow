package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.application.DocumentCollaborationService;
import com.contentworkflow.document.application.DocumentPermissionService;
import com.contentworkflow.document.domain.entity.CollaborativeDocument;
import com.contentworkflow.document.domain.entity.DocumentOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultDocumentRealtimeGatewayFacade implements DocumentRealtimeGatewayFacade {

    private static final String INSTRUCTION_TYPE_PULL_SNAPSHOT = "PULL_SNAPSHOT_REQUIRED";
    private static final String INSTRUCTION_TYPE_PULL_UPDATES = "PULL_UPDATES_REQUIRED";

    private final DocumentCollaborationService documentService;
    private final DocumentPermissionService permissionService;
    private final DocumentOperationService operationService;
    private final DocumentRealtimeRecentUpdateCache recentUpdateCache;
    private final boolean strictStateless;

    public DefaultDocumentRealtimeGatewayFacade(
            DocumentCollaborationService documentService,
            DocumentPermissionService permissionService,
            DocumentOperationService operationService,
            DocumentRealtimeRecentUpdateCache recentUpdateCache,
            @Value("${workflow.realtime.gateway.strict-stateless:true}") boolean strictStateless) {
        this.documentService = documentService;
        this.permissionService = permissionService;
        this.operationService = operationService;
        this.recentUpdateCache = recentUpdateCache;
        this.strictStateless = strictStateless;
    }

    @Override
    public JoinDecision prepareJoin(Long documentId, String editorId) {
        if (strictStateless) {
            return JoinDecision.instruction(
                    INSTRUCTION_TYPE_PULL_SNAPSHOT,
                    "strict_stateless_join: pull snapshot via HTTP GET /api/docs/" + documentId
            );
        }
        permissionService.requireMember(documentId, editorId);
        CollaborativeDocument document = documentService.getDocument(documentId);
        return JoinDecision.snapshot(
                document.getLatestRevision(),
                document.getTitle(),
                document.getContent()
        );
    }

    @Override
    public void authorizeMember(Long documentId, String editorId) {
        if (strictStateless) {
            return;
        }
        permissionService.requireMember(documentId, editorId);
    }

    @Override
    public void authorizeCanEdit(Long documentId, String editorId) {
        if (strictStateless) {
            return;
        }
        permissionService.requireCanEdit(documentId, editorId);
    }

    @Override
    public SyncDecision prepareSync(Long documentId, String editorId, int fromRevision, int limit) {
        if (strictStateless) {
            DocumentRealtimeRecentUpdateCache.ReplayResult cached = recentUpdateCache.replaySince(documentId, fromRevision, limit);
            if (cached.completeFromBase()) {
                return SyncDecision.replay(cached.operations(), resolveLatestRevision(fromRevision, cached.operations()));
            }
            return SyncDecision.instruction(
                    resolveLatestRevision(fromRevision, cached.operations()),
                    INSTRUCTION_TYPE_PULL_UPDATES,
                    "strict_stateless_sync: pull updates via HTTP GET /api/docs/" + documentId
                            + "/operations?fromRevision=" + fromRevision + "&limit=" + limit
            );
        }

        permissionService.requireMember(documentId, editorId);
        CollaborativeDocument latest = documentService.getDocument(documentId);
        int latestRevision = latest.getLatestRevision() == null ? 0 : latest.getLatestRevision();
        if (latestRevision <= fromRevision) {
            return SyncDecision.replay(List.of(), latestRevision);
        }
        DocumentRealtimeRecentUpdateCache.ReplayResult cached = recentUpdateCache.replaySince(documentId, fromRevision, limit);
        if (cached.completeFromBase()) {
            return SyncDecision.replay(cached.operations(), latestRevision);
        }
        return SyncDecision.replay(
                operationService.listOperationsSince(documentId, fromRevision, limit),
                latestRevision
        );
    }

    @Override
    public ConflictDecision resolveConflict(Long documentId) {
        if (strictStateless) {
            return ConflictDecision.instruction(
                    INSTRUCTION_TYPE_PULL_SNAPSHOT,
                    "strict_stateless_conflict: pull latest snapshot via HTTP GET /api/docs/" + documentId
            );
        }
        CollaborativeDocument latest = documentService.getDocument(documentId);
        return ConflictDecision.snapshot(
                latest.getLatestRevision(),
                latest.getTitle(),
                latest.getContent()
        );
    }

    private int resolveLatestRevision(int fromRevision, List<DocumentOperation> operations) {
        if (operations == null || operations.isEmpty()) {
            return fromRevision;
        }
        DocumentOperation last = operations.get(operations.size() - 1);
        if (last == null || last.getRevisionNo() == null) {
            return fromRevision;
        }
        return Math.max(fromRevision, last.getRevisionNo());
    }
}
