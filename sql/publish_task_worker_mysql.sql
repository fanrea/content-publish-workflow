-- MySQL 8.x: publish task worker reference queries
-- This file is for documentation/review only. It is not executed by the service.

-- 1) Claim runnable tasks (one possible approach):
-- Use a transaction + SELECT ... FOR UPDATE SKIP LOCKED to claim without duplicates.
--
-- NOTE: Spring Data JPA in this repo uses PESSIMISTIC_WRITE + pageable to approximate this.

START TRANSACTION;

SELECT id
FROM content_publish_task
WHERE task_status IN ('PENDING', 'FAILED')
  AND (next_run_at IS NULL OR next_run_at <= NOW())
  AND (locked_at IS NULL OR locked_at < DATE_SUB(NOW(), INTERVAL 60 SECOND))
ORDER BY updated_at ASC
LIMIT 20
FOR UPDATE SKIP LOCKED;

-- For claimed ids, mark RUNNING + lock metadata
-- UPDATE content_publish_task
-- SET task_status='RUNNING', locked_by='worker-1', locked_at=NOW(), updated_at=NOW()
-- WHERE id IN (...);

COMMIT;

-- 2) Mark success
-- UPDATE content_publish_task
-- SET task_status='SUCCESS', error_message=NULL, next_run_at=NULL, locked_by=NULL, locked_at=NULL, updated_at=NOW()
-- WHERE id = ? AND task_status='RUNNING';

-- 3) Mark failure with backoff
-- UPDATE content_publish_task
-- SET task_status='FAILED',
--     retry_times=retry_times+1,
--     error_message=?,
--     next_run_at=DATE_ADD(NOW(), INTERVAL 30 SECOND),
--     locked_by=NULL,
--     locked_at=NULL,
--     updated_at=NOW()
-- WHERE id = ? AND task_status='RUNNING';

-- 4) Dead-letter
-- UPDATE content_publish_task
-- SET task_status='DEAD', error_message=?, next_run_at=NULL, locked_by=NULL, locked_at=NULL, updated_at=NOW()
-- WHERE id = ? AND retry_times >= 5;

