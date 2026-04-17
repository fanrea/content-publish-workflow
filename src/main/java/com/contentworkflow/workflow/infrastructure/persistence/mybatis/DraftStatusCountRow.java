package com.contentworkflow.workflow.infrastructure.persistence.mybatis;

import com.contentworkflow.workflow.domain.enums.WorkflowStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DraftStatusCountRow {

    private WorkflowStatus status;
    private Long cnt;
}
