-- MySQL 8.x: Outbox + RabbitMQ relay reference (SQL only).
--
-- Purpose:
-- - Provide a reliable way to publish domain events without distributed transactions.
-- - Business transaction writes "workflow_outbox_event".
-- - Relay worker scans NEW/FAILED rows and sends to RabbitMQ.

-- Claim candidates (one possible approach with SKIP LOCKED):
START TRANSACTION;

SELECT id
FROM workflow_outbox_event
WHERE status IN ('NEW', 'FAILED')
  AND (next_retry_at IS NULL OR next_retry_at <= NOW(3))
  AND (locked_at IS NULL OR locked_at < DATE_SUB(NOW(3), INTERVAL 60 SECOND))
ORDER BY created_at ASC
LIMIT 50
FOR UPDATE SKIP LOCKED;

-- Mark SENDING + lock
-- UPDATE workflow_outbox_event
-- SET status='SENDING', locked_by='outbox-worker-1', locked_at=NOW(3), updated_at=NOW(3)
-- WHERE id IN (...);

COMMIT;

-- Mark SENT
-- UPDATE workflow_outbox_event
-- SET status='SENT', sent_at=NOW(3), error_message=NULL, next_retry_at=NULL,
--     locked_by=NULL, locked_at=NULL, updated_at=NOW(3)
-- WHERE id = ? AND status='SENDING';

-- Mark FAILED with backoff
-- UPDATE workflow_outbox_event
-- SET status='FAILED',
--     attempt=attempt+1,
--     error_message=?,
--     next_retry_at=DATE_ADD(NOW(3), INTERVAL 30 SECOND),
--     locked_by=NULL,
--     locked_at=NULL,
--     updated_at=NOW(3)
-- WHERE id = ? AND status='SENDING';

