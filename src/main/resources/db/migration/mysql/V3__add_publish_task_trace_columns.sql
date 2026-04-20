ALTER TABLE content_publish_task
    ADD COLUMN trace_id VARCHAR(128) NULL AFTER published_version,
    ADD COLUMN request_id VARCHAR(128) NULL AFTER trace_id;

CREATE INDEX idx_task_trace_id ON content_publish_task (trace_id, created_at);
CREATE INDEX idx_task_request_id ON content_publish_task (request_id, created_at);
