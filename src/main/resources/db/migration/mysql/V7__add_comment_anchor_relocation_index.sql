ALTER TABLE collaborative_document_comment
    ADD KEY idx_document_comment_open_anchor (document_id, status, end_offset);
