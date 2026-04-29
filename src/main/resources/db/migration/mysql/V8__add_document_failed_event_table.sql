CREATE TABLE collaborative_document_failed_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_key VARCHAR(128) NOT NULL,
    event_payload LONGTEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_error VARCHAR(500) NULL,
    locked_by VARCHAR(64) NULL,
    locked_at DATETIME(3) NULL,
    sent_at DATETIME(3) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_doc_failed_event_key (event_key),
    KEY idx_doc_failed_event_retry (status, next_retry_at, id),
    KEY idx_doc_failed_event_lock (locked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
