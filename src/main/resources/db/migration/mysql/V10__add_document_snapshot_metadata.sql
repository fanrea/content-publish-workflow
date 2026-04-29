ALTER TABLE collaborative_document
    ADD COLUMN latest_snapshot_ref VARCHAR(255) NULL COMMENT 'latest snapshot reference',
    ADD COLUMN latest_snapshot_revision INT NULL COMMENT 'latest snapshot revision';

CREATE INDEX idx_collaborative_document_latest_snapshot_revision
    ON collaborative_document (latest_snapshot_revision);
