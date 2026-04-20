ALTER TABLE workflow_outbox_event
    ADD COLUMN trace_id VARCHAR(64) NULL AFTER headers_json,
    ADD COLUMN request_id VARCHAR(64) NULL AFTER trace_id;

UPDATE workflow_outbox_event
SET trace_id = COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(headers_json, '$."X-Trace-Id"')), ''),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(headers_json, '$.traceId')), ''),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(headers_json, '$."X-B3-TraceId"')), '')
    ),
    request_id = COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(headers_json, '$."X-Request-Id"')), ''),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(headers_json, '$.requestId')), '')
    )
WHERE headers_json IS NOT NULL
  AND JSON_VALID(headers_json)
  AND (trace_id IS NULL OR request_id IS NULL);

CREATE INDEX idx_outbox_trace_created ON workflow_outbox_event (trace_id, created_at);
