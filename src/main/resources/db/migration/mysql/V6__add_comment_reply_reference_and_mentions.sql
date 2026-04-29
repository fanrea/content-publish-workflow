ALTER TABLE collaborative_document_comment_reply
    ADD COLUMN reply_to_reply_id BIGINT NULL AFTER comment_id,
    ADD COLUMN mention_member_ids VARCHAR(1000) NULL AFTER content,
    ADD KEY idx_comment_reply_reply_to (comment_id, reply_to_reply_id),
    ADD CONSTRAINT fk_comment_reply_reply_to
        FOREIGN KEY (reply_to_reply_id) REFERENCES collaborative_document_comment_reply(id);
