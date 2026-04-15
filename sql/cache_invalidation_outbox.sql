-- Optional enhancement: cache invalidation outbox (SQL only, not executed automatically).
--
-- Background:
-- - Current cache strategy mainly relies on @CacheEvict + TTL, which is fine for simple single-service usage.
-- - If the publish workflow introduces more async side effects (refresh index / notify downstream),
--   it is recommended to standardize "what happened" as outbox events, and let a worker do cache evict/warmup.
--
-- Recommended usage:
-- 1) In the same transaction of the business write, insert an outbox event.
-- 2) A worker scans NEW events and performs cache evict/warmup.
-- 3) Mark the event DONE (or FAILED with retries).

CREATE TABLE IF NOT EXISTS cache_invalidation_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    aggregate_type VARCHAR(64) NOT NULL COMMENT 'Aggregate type, e.g. content_draft',
    aggregate_id BIGINT NOT NULL COMMENT 'Aggregate id, e.g. draftId',
    event_type VARCHAR(64) NOT NULL COMMENT 'Event type, e.g. DRAFT_UPDATED / DRAFT_PUBLISHED',
    payload_json TEXT NULL COMMENT 'Optional JSON payload',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=NEW, 1=PROCESSING, 2=DONE, 3=FAILED',
    attempt INT NOT NULL DEFAULT 0 COMMENT 'Retry times',
    error_message VARCHAR(512) NULL COMMENT 'Last error message',
    created_at DATETIME(3) NOT NULL,
    processed_at DATETIME(3) NULL,
    INDEX idx_outbox_status_created (status, created_at),
    INDEX idx_outbox_aggregate (aggregate_type, aggregate_id)
) COMMENT='Cache invalidation outbox (optional)';

