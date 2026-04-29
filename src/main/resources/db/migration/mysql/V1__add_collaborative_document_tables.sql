CREATE TABLE collaborative_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lock_version BIGINT NOT NULL DEFAULT 0,
    doc_no VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content LONGTEXT NOT NULL,
    latest_revision INT NOT NULL DEFAULT 1,
    created_by VARCHAR(64) NULL,
    updated_by VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_collaborative_document_doc_no (doc_no),
    KEY idx_collaborative_document_updated_at (updated_at),
    KEY idx_collaborative_document_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE collaborative_document_revision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    base_revision INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content LONGTEXT NOT NULL,
    editor_id VARCHAR(64) NULL,
    editor_name VARCHAR(64) NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    change_summary VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_revision (document_id, revision_no),
    KEY idx_document_revision_created (document_id, created_at),
    CONSTRAINT fk_document_revision_document
        FOREIGN KEY (document_id) REFERENCES collaborative_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
