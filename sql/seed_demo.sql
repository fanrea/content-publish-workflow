-- Demo seed data for local dev / load test.
-- This script is optional. It helps generate stable data volume for list/stats endpoints.
-- Target DB: MySQL 8.x (run after sql/schema.sql).

SET NAMES utf8mb4;

-- 1) Generate N drafts (default 1000).
-- You can change the N value by editing the WHERE clause.
INSERT INTO content_draft (
    biz_no, title, summary, body,
    draft_version, published_version, workflow_status,
    current_snapshot_id, last_review_comment,
    created_at, updated_at
)
SELECT
    CONCAT('DEMO-', LPAD(seq.n, 6, '0')) AS biz_no,
    CONCAT('Demo Draft ', seq.n) AS title,
    CONCAT('Seed summary ', seq.n) AS summary,
    CONCAT('Seed body ', seq.n, ' - Lorem ipsum dolor sit amet.') AS body,
    1 + (seq.n % 3) AS draft_version,
    CASE
        WHEN (seq.n % 8) IN (5, 6) THEN 1 + (seq.n % 5)
        ELSE 0
    END AS published_version,
    CASE (seq.n % 8)
        WHEN 0 THEN 'DRAFT'
        WHEN 1 THEN 'REVIEWING'
        WHEN 2 THEN 'APPROVED'
        WHEN 3 THEN 'REJECTED'
        WHEN 4 THEN 'PUBLISHING'
        WHEN 5 THEN 'PUBLISHED'
        WHEN 6 THEN 'OFFLINE'
        ELSE 'PUBLISH_FAILED'
    END AS workflow_status,
    NULL AS current_snapshot_id,
    NULL AS last_review_comment,
    DATE_SUB(NOW(), INTERVAL (seq.n % 30) DAY) AS created_at,
    DATE_SUB(NOW(), INTERVAL (seq.n % 7) DAY) AS updated_at
FROM (
    SELECT (a.n + b.n * 10 + c.n * 100 + d.n * 1000) + 1 AS n
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d
) seq
WHERE seq.n <= 1000;

-- 2) Create snapshots for drafts that look "published" or "offline".
INSERT INTO content_publish_snapshot (
    draft_id,
    published_version,
    source_draft_version,
    title,
    summary,
    body,
    operator_name,
    rollback_flag,
    published_at
)
SELECT
    d.id,
    d.published_version,
    d.draft_version,
    d.title,
    d.summary,
    d.body,
    'seed' AS operator_name,
    0 AS rollback_flag,
    d.updated_at AS published_at
FROM content_draft d
WHERE d.published_version > 0
  AND d.workflow_status IN ('PUBLISHED', 'OFFLINE')
  AND NOT EXISTS (
      SELECT 1
      FROM content_publish_snapshot s
      WHERE s.draft_id = d.id
        AND s.published_version = d.published_version
  );

