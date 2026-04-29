CREATE TABLE collaborative_document_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    start_offset INT NOT NULL,
    end_offset INT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_by_id VARCHAR(64) NOT NULL,
    created_by_name VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_by_id VARCHAR(64) NULL,
    resolved_by_name VARCHAR(64) NULL,
    resolved_at DATETIME NULL,
    KEY idx_document_comment_doc_created (document_id, created_at),
    KEY idx_document_comment_doc_status (document_id, status),
    CONSTRAINT fk_document_comment_document
        FOREIGN KEY (document_id) REFERENCES collaborative_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

