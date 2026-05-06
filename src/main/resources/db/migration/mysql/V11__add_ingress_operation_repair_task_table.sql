CREATE TABLE operation_repair_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_id BIGINT NULL,
    session_id VARCHAR(128) NULL,
    client_seq BIGINT NULL,
    payload LONGTEXT NOT NULL,
    failure_reason VARCHAR(500) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_operation_repair_dedup (doc_id, session_id, client_seq),
    KEY idx_operation_repair_status_created (status, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
