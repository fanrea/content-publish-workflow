CREATE TABLE collaborative_document_comment_reply (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    comment_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_by_id VARCHAR(64) NOT NULL,
    created_by_name VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_comment_reply_comment_created (comment_id, created_at),
    KEY idx_comment_reply_doc_created (document_id, created_at),
    CONSTRAINT fk_comment_reply_document
        FOREIGN KEY (document_id) REFERENCES collaborative_document(id),
    CONSTRAINT fk_comment_reply_comment
        FOREIGN KEY (comment_id) REFERENCES collaborative_document_comment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

