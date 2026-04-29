CREATE TABLE collaborative_document_operation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    base_revision INT NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    client_seq BIGINT NOT NULL,
    op_type VARCHAR(16) NOT NULL,
    op_position INT NOT NULL,
    op_length INT NOT NULL,
    op_text TEXT NULL,
    editor_id VARCHAR(64) NULL,
    editor_name VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_session_seq (document_id, session_id, client_seq),
    UNIQUE KEY uk_document_revision (document_id, revision_no),
    KEY idx_document_created (document_id, created_at),
    CONSTRAINT fk_document_operation_document
        FOREIGN KEY (document_id) REFERENCES collaborative_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
