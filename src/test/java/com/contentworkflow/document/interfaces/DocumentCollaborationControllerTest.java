package com.contentworkflow.document.interfaces;

import com.contentworkflow.common.api.ApiResponse;
import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.document.application.DocumentCollaborationService;
import com.contentworkflow.document.application.DocumentCommentService;
import com.contentworkflow.document.application.DocumentPermissionService;
import com.contentworkflow.document.application.realtime.DocumentOperationService;
import com.contentworkflow.document.application.realtime.DocumentRealtimePushService;
import com.contentworkflow.document.domain.entity.CollaborativeDocument;
import com.contentworkflow.document.domain.entity.DocumentOperation;
import com.contentworkflow.document.domain.entity.DocumentRevision;
import com.contentworkflow.document.domain.enums.DocumentChangeType;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.interfaces.dto.ApplyDocumentOperationRequest;
import com.contentworkflow.document.interfaces.dto.RestoreDocumentRevisionRequest;
import com.contentworkflow.document.interfaces.dto.UpdateDocumentRequest;
import com.contentworkflow.document.interfaces.vo.CollaborativeDocumentResponse;
import com.contentworkflow.document.interfaces.vo.DocumentOperationApplyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;

@ExtendWith(MockitoExtension.class)
class DocumentCollaborationControllerTest {

    @Mock
    private DocumentCollaborationService documentService;
    @Mock
    private DocumentPermissionService permissionService;
    @Mock
    private DocumentOperationService operationService;
    @Mock
    private DocumentCommentService commentService;
    @Mock
    private DocumentRealtimePushService realtimePushService;

    private DocumentCollaborationController controller;

    @BeforeEach
    void setUp() {
        controller = new DocumentCollaborationController(
                documentService,
                permissionService,
                operationService,
                commentService,
                realtimePushService
        );
    }

    @Test
    void updateDocument_shouldRouteToOperationPipelineAndForwardClientHeaders() {
        CollaborativeDocument updated = CollaborativeDocument.builder()
                .id(10L)
                .version(9L)
                .docNo("DOC-10")
                .title("new-title")
                .content("new-content")
                .latestRevision(3)
                .createdBy("alice")
                .updatedBy("alice")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(operationService.applyFullReplaceOperation(
                10L,
                2,
                "client-session-1",
                9001L,
                "u1",
                "Alice",
                "new-title",
                "new-content",
                "summary"
        )).thenReturn(new DocumentOperationService.ApplyResult(false, updated, null, null));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Editor-Id", "u1");
        httpRequest.addHeader("X-Editor-Name", "Alice");
        httpRequest.addHeader("X-Client-Session-Id", "client-session-1");
        httpRequest.addHeader("X-Client-Seq", "9001");

        ApiResponse<CollaborativeDocumentResponse> response = controller.updateDocument(
                10L,
                new UpdateDocumentRequest(2, null, "new-title", "new-content", "summary"),
                httpRequest
        );

        assertThat(response.data()).isNotNull();
        assertThat(response.data().title()).isEqualTo("new-title");
        assertThat(response.data().latestRevision()).isEqualTo(3);
        verify(operationService).applyFullReplaceOperation(
                10L,
                2,
                "client-session-1",
                9001L,
                "u1",
                "Alice",
                "new-title",
                "new-content",
                "summary"
        );
        verifyNoInteractions(documentService);
    }

    @Test
    void updateDocument_shouldRejectInvalidClientSeqHeader() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Editor-Id", "u1");
        httpRequest.addHeader("X-Editor-Name", "Alice");
        httpRequest.addHeader("X-Client-Seq", "not-a-long");

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.updateDocument(
                10L,
                new UpdateDocumentRequest(2, null, "new-title", "new-content", "summary"),
                httpRequest
        ));

        assertThat(exception.getCode()).isEqualTo("INVALID_ARGUMENT");
        verifyNoInteractions(operationService);
    }

    @Test
    void updateDocument_shouldRejectWhenExpectedRevisionAndBaseRevisionMismatch() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Editor-Id", "u1");
        httpRequest.addHeader("X-Editor-Name", "Alice");

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.updateDocument(
                10L,
                new UpdateDocumentRequest(3, 2, "new-title", "new-content", "summary"),
                httpRequest
        ));

        assertThat(exception.getCode()).isEqualTo("INVALID_ARGUMENT");
        verifyNoInteractions(operationService);
    }

    @Test
    void updateDocument_shouldRejectWhenRevisionMissing() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Editor-Id", "u1");
        httpRequest.addHeader("X-Editor-Name", "Alice");

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.updateDocument(
                10L,
                new UpdateDocumentRequest(null, null, "new-title", "new-content", "summary"),
                httpRequest
        ));

        assertThat(exception.getCode()).isEqualTo("INVALID_ARGUMENT");
        verifyNoInteractions(operationService);
    }

    @Test
    void applyOperation_shouldRouteToOperationPipeline() {
        CollaborativeDocument updated = CollaborativeDocument.builder()
                .id(10L)
                .version(9L)
                .docNo("DOC-10")
                .title("new-title")
                .content("new-content")
                .latestRevision(3)
                .build();
        DocumentOperation operation = DocumentOperation.builder()
                .id(200L)
                .documentId(10L)
                .revisionNo(3)
                .baseRevision(2)
                .clientSeq(9200L)
                .opType(DocumentOpType.INSERT)
                .opPosition(1)
                .opLength(0)
                .opText("X")
                .editorId("u1")
                .editorName("Alice")
                .build();
        DocumentRevision revision = DocumentRevision.builder()
                .id(300L)
                .documentId(10L)
                .revisionNo(3)
                .baseRevision(2)
                .title("new-title")
                .content(null)
                .build();
        when(operationService.applyOperation(
                eq(10L),
                eq(2),
                eq("client-session-op"),
                eq(9200L),
                eq("u1"),
                eq("Alice"),
                org.mockito.ArgumentMatchers.any(),
                eq("new-title"),
                eq("summary")
        )).thenReturn(new DocumentOperationService.ApplyResult(false, updated, operation, revision));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Editor-Id", "u1");
        httpRequest.addHeader("X-Editor-Name", "Alice");
        httpRequest.addHeader("X-Client-Session-Id", "client-session-op");
        httpRequest.addHeader("X-Client-Seq", "9200");

        ApiResponse<DocumentOperationApplyResponse> response = controller.applyOperation(
                10L,
                new ApplyDocumentOperationRequest(
                        2,
                        null,
                        DocumentOpType.INSERT,
                        1,
                        0,
                        "X",
                        "new-title",
                        "summary"
                ),
                httpRequest
        );

        assertThat(response.data()).isNotNull();
        assertThat(response.data().duplicated()).isFalse();
        assertThat(response.data().document().latestRevision()).isEqualTo(3);
        assertThat(response.data().operation().opType()).isEqualTo(DocumentOpType.INSERT);
    }

    @Test
    void restoreRevision_shouldRouteToOperationPipelineWithRestoreChangeType() {
        DocumentRevision targetRevision = DocumentRevision.builder()
                .id(21L)
                .documentId(10L)
                .revisionNo(2)
                .baseRevision(1)
                .title("restored-title")
                .content("restored-content")
                .build();
        CollaborativeDocument updated = CollaborativeDocument.builder()
                .id(10L)
                .version(10L)
                .docNo("DOC-10")
                .title("restored-title")
                .content("restored-content")
                .latestRevision(3)
                .build();
        when(documentService.getRevision(10L, 2)).thenReturn(targetRevision);
        when(operationService.applyFullReplaceOperation(
                eq(10L),
                eq(2),
                eq("client-session-restore"),
                eq(9100L),
                eq("owner-1"),
                eq("Owner"),
                eq("restored-title"),
                eq("restored-content"),
                eq("restore from revision 2"),
                eq(DocumentChangeType.RESTORE)
        )).thenReturn(new DocumentOperationService.ApplyResult(false, updated, null, null));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Editor-Id", "owner-1");
        httpRequest.addHeader("X-Editor-Name", "Owner");
        httpRequest.addHeader("X-Client-Session-Id", "client-session-restore");
        httpRequest.addHeader("X-Client-Seq", "9100");

        ApiResponse<CollaborativeDocumentResponse> response = controller.restoreRevision(
                10L,
                new RestoreDocumentRevisionRequest(2, 2, null),
                httpRequest
        );

        assertThat(response.data()).isNotNull();
        assertThat(response.data().title()).isEqualTo("restored-title");
        verify(permissionService).requireOwner(10L, "owner-1");
        verify(documentService).getRevision(10L, 2);
        verify(operationService).applyFullReplaceOperation(
                10L,
                2,
                "client-session-restore",
                9100L,
                "owner-1",
                "Owner",
                "restored-title",
                "restored-content",
                "restore from revision 2",
                DocumentChangeType.RESTORE
        );
    }
}
