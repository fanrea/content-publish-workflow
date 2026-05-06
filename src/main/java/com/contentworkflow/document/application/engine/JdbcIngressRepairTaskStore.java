package com.contentworkflow.document.application.engine;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcIngressRepairTaskStore implements IngressRepairTaskStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcIngressRepairTaskStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveOrUpdate(IngressRepairTask task) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                insert into operation_repair_task (
                    doc_id,
                    session_id,
                    client_seq,
                    payload,
                    failure_reason,
                    retry_count,
                    status,
                    created_at,
                    updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update
                    payload = values(payload),
                    failure_reason = values(failure_reason),
                    retry_count = values(retry_count),
                    status = values(status),
                    updated_at = values(updated_at)
                """,
                task.docId(),
                task.sessionId(),
                task.clientSeq(),
                task.payload(),
                trimFailureReason(task.failureReason()),
                Math.max(0, task.retryCount()),
                task.status(),
                now,
                now
        );
    }

    private String trimFailureReason(String failureReason) {
        if (failureReason == null) {
            return null;
        }
        String normalized = failureReason.trim();
        if (normalized.length() <= 500) {
            return normalized;
        }
        return normalized.substring(0, 500);
    }
}
