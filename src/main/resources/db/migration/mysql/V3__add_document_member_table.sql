CREATE TABLE collaborative_document_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    member_name VARCHAR(64) NOT NULL,
    member_role VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_member (document_id, member_id),
    KEY idx_document_member_role (document_id, member_role),
    CONSTRAINT fk_document_member_document
        FOREIGN KEY (document_id) REFERENCES collaborative_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO collaborative_document_member (
    document_id,
    member_id,
    member_name,
    member_role,
    created_at,
    updated_at
)
SELECT
    d.id,
    COALESCE(NULLIF(TRIM(d.created_by), ''), 'anonymous') AS member_id,
    COALESCE(NULLIF(TRIM(d.created_by), ''), 'anonymous') AS member_name,
    'OWNER' AS member_role,
    d.created_at,
    d.updated_at
FROM collaborative_document d;
