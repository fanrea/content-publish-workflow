package com.contentworkflow.document.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class JdbcFailedDocumentEventStore implements FailedDocumentEventStore {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RETRYING = "RETRYING";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_DEAD = "DEAD";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcFailedDocumentEventStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void saveFailure(String eventKey, DocumentDomainEvent event, String errorMessage, LocalDateTime now) {
        Long existingId = jdbcTemplate.query(
                "select id from collaborative_document_failed_event where event_key = ? limit 1",
                rs -> rs.next() ? rs.getLong("id") : null,
                eventKey
        );
        if (existingId == null) {
            jdbcTemplate.update(
                    """
                    insert into collaborative_document_failed_event (
                        event_key,
                        event_payload,
                        status,
                        attempt_count,
                        next_retry_at,
                        last_error,
                        created_at,
                        updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    eventKey,
                    writeEventJson(event),
                    STATUS_PENDING,
                    0,
                    now,
                    trimError(errorMessage),
                    now,
                    now
            );
            return;
        }
        jdbcTemplate.update(
                """
                update collaborative_document_failed_event
                   set status = ?,
                       next_retry_at = ?,
                       last_error = ?,
                       locked_by = null,
                       locked_at = null,
                       updated_at = ?
                 where id = ?
                   and status <> ?
                """,
                STATUS_PENDING,
                now,
                trimError(errorMessage),
                now,
                existingId,
                STATUS_SENT
        );
    }

    @Override
    @Transactional
    public List<StoredDocumentEvent> claimBatch(LocalDateTime now, String lockerId, long lockTimeoutMs, int limit) {
        LocalDateTime lockExpiredAt = now.minusNanos(lockTimeoutMs * 1_000_000L);
        List<StoredDocumentEvent> candidates = jdbcTemplate.query(
                """
                select id, event_key, event_payload, attempt_count
                  from collaborative_document_failed_event
                 where status in (?, ?)
                   and next_retry_at <= ?
                   and (locked_at is null or locked_at < ?)
                 order by id asc
                 limit ?
                """,
                (rs, rowNum) -> mapStoredEvent(rs),
                STATUS_PENDING,
                STATUS_RETRYING,
                now,
                lockExpiredAt,
                limit
        );
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<StoredDocumentEvent> claimed = new ArrayList<>(candidates.size());
        for (StoredDocumentEvent candidate : candidates) {
            int updated = jdbcTemplate.update(
                    """
                    update collaborative_document_failed_event
                       set locked_by = ?,
                           locked_at = ?,
                           updated_at = ?
                     where id = ?
                       and status in (?, ?)
                       and (locked_at is null or locked_at < ?)
                    """,
                    lockerId,
                    now,
                    now,
                    candidate.id(),
                    STATUS_PENDING,
                    STATUS_RETRYING,
                    lockExpiredAt
            );
            if (updated == 1) {
                claimed.add(candidate);
            }
        }
        return claimed;
    }

    @Override
    @Transactional
    public void markSent(Long id, LocalDateTime now) {
        jdbcTemplate.update(
                """
                update collaborative_document_failed_event
                   set status = ?,
                       sent_at = ?,
                       locked_by = null,
                       locked_at = null,
                       last_error = null,
                       updated_at = ?
                 where id = ?
                """,
                STATUS_SENT,
                now,
                now,
                id
        );
    }

    @Override
    @Transactional
    public void markRetry(Long id, int nextAttemptCount, LocalDateTime nextRetryAt, String errorMessage, boolean dead) {
        LocalDateTime resolvedNextRetryAt = dead
                ? LocalDateTime.now()
                : (nextRetryAt == null ? LocalDateTime.now() : nextRetryAt);
        jdbcTemplate.update(
                """
                update collaborative_document_failed_event
                   set status = ?,
                       attempt_count = ?,
                       next_retry_at = ?,
                       last_error = ?,
                       locked_by = null,
                       locked_at = null,
                       updated_at = ?
                 where id = ?
                """,
                dead ? STATUS_DEAD : STATUS_RETRYING,
                nextAttemptCount,
                resolvedNextRetryAt,
                trimError(errorMessage),
                LocalDateTime.now(),
                id
        );
    }

    private StoredDocumentEvent mapStoredEvent(ResultSet rs) throws SQLException {
        try {
            return new StoredDocumentEvent(
                    rs.getLong("id"),
                    rs.getString("event_key"),
                    objectMapper.readValue(rs.getString("event_payload"), DocumentDomainEvent.class),
                    rs.getInt("attempt_count")
            );
        } catch (Exception ex) {
            throw new SQLException("failed to deserialize event payload", ex);
        }
    }

    private String writeEventJson(DocumentDomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize document event", ex);
        }
    }

    private String trimError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        String normalized = errorMessage.trim();
        if (normalized.length() <= 500) {
            return normalized;
        }
        return normalized.substring(0, 500);
    }
}
