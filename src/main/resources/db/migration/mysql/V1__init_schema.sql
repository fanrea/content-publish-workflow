CREATE TABLE content_draft (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lock_version BIGINT NOT NULL DEFAULT 0,
    biz_no VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    draft_version INT NOT NULL DEFAULT 1,
    published_version INT NOT NULL DEFAULT 0,
    workflow_status VARCHAR(32) NOT NULL,
    current_snapshot_id BIGINT NULL,
    last_review_comment VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_content_draft_biz_no (biz_no),
    KEY idx_content_draft_status (workflow_status),
    KEY idx_content_draft_updated_at (updated_at),
    KEY idx_content_draft_created_at (created_at),
    KEY idx_content_draft_status_updated (workflow_status, updated_at),
    KEY idx_content_draft_status_created (workflow_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE draft_operation_lock (
    draft_id BIGINT PRIMARY KEY,
    operation_type VARCHAR(32) NOT NULL,
    target_published_version INT NULL,
    locked_by VARCHAR(128) NOT NULL,
    locked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    KEY idx_draft_operation_lock_expires (expires_at),
    CONSTRAINT fk_draft_operation_lock_draft
        FOREIGN KEY (draft_id) REFERENCES content_draft(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE content_review_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_id BIGINT NOT NULL,
    draft_version INT NOT NULL,
    reviewer VARCHAR(64) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    comment VARCHAR(500) NULL,
    reviewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_review_draft_version (draft_id, draft_version),
    KEY idx_review_draft_time (draft_id, reviewed_at),
    CONSTRAINT fk_review_record_draft
        FOREIGN KEY (draft_id) REFERENCES content_draft(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE content_publish_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_id BIGINT NOT NULL,
    published_version INT NOT NULL,
    source_draft_version INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    rollback_flag TINYINT(1) NOT NULL DEFAULT 0,
    published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_snapshot_draft_published_version (draft_id, published_version),
    KEY idx_snapshot_draft_time (draft_id, published_at),
    CONSTRAINT fk_publish_snapshot_draft
        FOREIGN KEY (draft_id) REFERENCES content_draft(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE content_publish_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_id BIGINT NOT NULL,
    published_version INT NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    task_status VARCHAR(32) NOT NULL,
    retry_times INT NOT NULL DEFAULT 0,
    error_message VARCHAR(500) NULL,
    next_run_at DATETIME NULL,
    locked_by VARCHAR(64) NULL,
    locked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_draft_version_type (draft_id, published_version, task_type),
    KEY idx_task_status (task_status),
    KEY idx_task_status_next_run (task_status, next_run_at),
    KEY idx_task_lock (locked_by, locked_at),
    KEY idx_task_draft_status (draft_id, task_status),
    CONSTRAINT fk_publish_task_draft
        FOREIGN KEY (draft_id) REFERENCES content_draft(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE content_publish_command (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_id BIGINT NOT NULL,
    command_type VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    remark VARCHAR(500) NULL,
    command_status VARCHAR(32) NOT NULL,
    target_published_version INT NOT NULL,
    snapshot_id BIGINT NULL,
    error_message VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_publish_command (draft_id, command_type, idempotency_key),
    KEY idx_publish_command_draft_time (draft_id, created_at),
    KEY idx_publish_command_status (command_status, updated_at),
    CONSTRAINT fk_publish_command_draft
        FOREIGN KEY (draft_id) REFERENCES content_draft(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE content_publish_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_id BIGINT NOT NULL,
    trace_id VARCHAR(64) NULL,
    request_id VARCHAR(64) NULL,
    action_type VARCHAR(32) NOT NULL,
    operator_id VARCHAR(64) NULL,
    operator_name VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL DEFAULT 'CONTENT_DRAFT',
    target_id BIGINT NULL,
    published_version INT NULL,
    task_id BIGINT NULL,
    outbox_event_id BIGINT NULL,
    before_status VARCHAR(32) NULL,
    after_status VARCHAR(32) NULL,
    action_result VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_log_draft_time (draft_id, created_at),
    KEY idx_log_trace_id (trace_id, created_at),
    CONSTRAINT fk_publish_log_draft
        FOREIGN KEY (draft_id) REFERENCES content_draft(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE workflow_outbox_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    aggregate_version INT NULL,
    exchange_name VARCHAR(128) NOT NULL,
    routing_key VARCHAR(256) NOT NULL,
    payload_json TEXT NOT NULL,
    headers_json TEXT NULL,
    status VARCHAR(16) NOT NULL,
    attempt INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NULL,
    locked_by VARCHAR(64) NULL,
    locked_at DATETIME(3) NULL,
    error_message VARCHAR(512) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    sent_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_status_next (status, next_retry_at, created_at),
    KEY idx_outbox_locked (locked_at),
    KEY idx_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Outbox events for reliable MQ delivery';
