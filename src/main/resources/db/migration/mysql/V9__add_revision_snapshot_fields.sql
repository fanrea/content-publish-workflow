ALTER TABLE collaborative_document_revision
    ADD COLUMN is_snapshot TINYINT(1) NOT NULL DEFAULT 0 AFTER content;

ALTER TABLE collaborative_document_revision
    MODIFY COLUMN content LONGTEXT NULL;

UPDATE collaborative_document_revision
SET is_snapshot = 1
WHERE content IS NOT NULL;

CREATE INDEX idx_document_revision_snapshot
    ON collaborative_document_revision (document_id, is_snapshot, revision_no);
