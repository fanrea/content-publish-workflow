-- Embedded DB init schema (H2 in MySQL compatibility mode).
--
-- Notes:
-- 1) Spring Boot uses this file only for embedded database initialization.
-- 2) For MySQL, use the repository root file: sql/schema.sql.
-- 3) Keep this script H2-friendly and avoid MySQL-only clauses such as
--    ON UPDATE CURRENT_TIMESTAMP.

DROP TABLE IF EXISTS content_publish_log;
DROP TABLE IF EXISTS content_publish_command;
DROP TABLE IF EXISTS content_publish_task;
DROP TABLE IF EXISTS content_publish_snapshot;
DROP TABLE IF EXISTS content_review_record;
DROP TABLE IF EXISTS draft_operation_lock;
DROP TABLE IF EXISTS content_draft;
DROP TABLE IF EXISTS workflow_outbox_event;

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
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_content_draft_biz_no UNIQUE (biz_no)
);

CREATE TABLE draft_operation_lock (
    draft_id BIGINT PRIMARY KEY,
    operation_type VARCHAR(32) NOT NULL,
    target_published_version INT NULL,
    locked_by VARCHAR(128) NOT NULL,
    locked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    CONSTRAINT fk_draft_operation_lock_draft FOREIGN KEY (draft_id) REFERENCES content_draft(id)
);

CREATE TABLE content_review_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_id BIGINT NOT NULL,
    draft_version INT NOT NULL,
    reviewer VARCHAR(64) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    comment VARCHAR(500) NULL,
    reviewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_record_draft FOREIGN KEY (draft_id) REFERENCES content_draft(id)
);

CREATE TABLE content_publish_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_id BIGINT NOT NULL,
    published_version INT NOT NULL,
    source_draft_version INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    rollback_flag BOOLEAN NOT NULL DEFAULT FALSE,
    published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_snapshot_draft_version UNIQUE (draft_id, published_version),
    CONSTRAINT fk_publish_snapshot_draft FOREIGN KEY (draft_id) REFERENCES content_draft(id)
);

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
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_task_draft_version_type UNIQUE (draft_id, published_version, task_type),
    CONSTRAINT fk_publish_task_draft FOREIGN KEY (draft_id) REFERENCES content_draft(id)
);

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
    CONSTRAINT fk_publish_log_draft FOREIGN KEY (draft_id) REFERENCES content_draft(id)
);

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
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_publish_command UNIQUE (draft_id, command_type, idempotency_key),
    CONSTRAINT fk_publish_command_draft FOREIGN KEY (draft_id) REFERENCES content_draft(id)
);

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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX idx_content_draft_status ON content_draft (workflow_status);
CREATE INDEX idx_content_draft_updated_at ON content_draft (updated_at);
CREATE INDEX idx_content_draft_created_at ON content_draft (created_at);
CREATE INDEX idx_content_draft_status_updated ON content_draft (workflow_status, updated_at);
CREATE INDEX idx_content_draft_status_created ON content_draft (workflow_status, created_at);
CREATE INDEX idx_draft_operation_lock_expires ON draft_operation_lock (expires_at);

CREATE INDEX idx_review_draft_version ON content_review_record (draft_id, draft_version);
CREATE INDEX idx_review_draft_time ON content_review_record (draft_id, reviewed_at);

CREATE INDEX idx_snapshot_draft_time ON content_publish_snapshot (draft_id, published_at);

CREATE INDEX idx_task_status ON content_publish_task (task_status);
CREATE INDEX idx_task_status_next_run ON content_publish_task (task_status, next_run_at);
CREATE INDEX idx_task_lock ON content_publish_task (locked_by, locked_at);
CREATE INDEX idx_task_draft_status ON content_publish_task (draft_id, task_status);

CREATE INDEX idx_log_draft_time ON content_publish_log (draft_id, created_at);
CREATE INDEX idx_log_trace_id ON content_publish_log (trace_id, created_at);

CREATE INDEX idx_publish_command_draft_time ON content_publish_command (draft_id, created_at);
CREATE INDEX idx_publish_command_status ON content_publish_command (command_status, updated_at);

CREATE INDEX idx_outbox_status_next ON workflow_outbox_event (status, next_retry_at, created_at);
CREATE INDEX idx_outbox_locked ON workflow_outbox_event (locked_at);
CREATE INDEX idx_outbox_aggregate ON workflow_outbox_event (aggregate_type, aggregate_id);
