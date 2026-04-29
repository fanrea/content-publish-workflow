package com.contentworkflow.document.domain.entity;

import com.contentworkflow.document.domain.enums.DocumentOpType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单次文档操作记录（用于操作回放和幂等去重）。
 */
@Data
@Builder
public class DocumentOperation {
    private Long id;
    private Long documentId;
    private Integer revisionNo;
    private Integer baseRevision;
    private String sessionId;
    private Long clientSeq;
    private DocumentOpType opType;
    private Integer opPosition;
    private Integer opLength;
    private String opText;
    private String editorId;
    private String editorName;
    private LocalDateTime createdAt;
}
