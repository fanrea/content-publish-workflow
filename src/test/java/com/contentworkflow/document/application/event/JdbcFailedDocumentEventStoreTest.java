package com.contentworkflow.document.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcFailedDocumentEventStoreTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcFailedDocumentEventStore store;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:event-store-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop table if exists collaborative_document_failed_event");
        jdbcTemplate.execute("""
                create table collaborative_document_failed_event (
                    id bigint primary key auto_increment,
                    event_key varchar(128) not null,
                    event_payload text not null,
                    status varchar(16) not null,
                    attempt_count int not null default 0,
                    next_retry_at datetime(3) not null,
                    last_error varchar(500) null,
                    locked_by varchar(64) null,
                    locked_at datetime(3) null,
                    sent_at datetime(3) null,
                    created_at datetime not null,
                    updated_at datetime not null,
                    unique (event_key)
                )
                """);
        store = new JdbcFailedDocumentEventStore(
                jdbcTemplate,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void saveClaimRetryAndMarkSent_shouldWorkEndToEnd() {
        LocalDateTime now = LocalDateTime.now();
        DocumentDomainEvent event = new DocumentDomainEvent(
                "DOCUMENT_EDITED",
                1L,
                2,
                "u1",
                "alice",
                Map.of("k", "v"),
                now
        );
        store.saveFailure("k1", event, "send failed", now);

        List<StoredDocumentEvent> firstClaim = store.claimBatch(now.plusSeconds(1), "node-1", 30000, 10);
        assertThat(firstClaim).hasSize(1);
        StoredDocumentEvent claimed = firstClaim.get(0);
        assertThat(claimed.event().eventType()).isEqualTo("DOCUMENT_EDITED");

        store.markRetry(claimed.id(), 1, now.plusSeconds(60), "retry failed", false);

        List<StoredDocumentEvent> earlyClaim = store.claimBatch(now.plusSeconds(10), "node-1", 30000, 10);
        assertThat(earlyClaim).isEmpty();

        List<StoredDocumentEvent> secondClaim = store.claimBatch(now.plusSeconds(70), "node-1", 30000, 10);
        assertThat(secondClaim).hasSize(1);
        assertThat(secondClaim.get(0).attemptCount()).isEqualTo(1);

        store.markSent(secondClaim.get(0).id(), now.plusSeconds(70));

        List<StoredDocumentEvent> afterSent = store.claimBatch(now.plusSeconds(80), "node-1", 30000, 10);
        assertThat(afterSent).isEmpty();
    }

    @Test
    void markRetryDead_shouldNotBreakOnNonNullNextRetryAtConstraint() {
        LocalDateTime now = LocalDateTime.now();
        DocumentDomainEvent event = new DocumentDomainEvent(
                "DOCUMENT_EDITED",
                2L,
                4,
                "u2",
                "bob",
                Map.of("k", "v2"),
                now
        );
        store.saveFailure("k-dead", event, "send failed", now);

        List<StoredDocumentEvent> claimed = store.claimBatch(now.plusSeconds(1), "node-2", 30000, 10);
        assertThat(claimed).hasSize(1);

        store.markRetry(claimed.get(0).id(), 3, null, "max attempts reached", true);

        Integer deadCount = jdbcTemplate.queryForObject(
                "select count(1) from collaborative_document_failed_event where event_key = ? and status = 'DEAD' and next_retry_at is not null",
                Integer.class,
                "k-dead"
        );
        assertThat(deadCount).isEqualTo(1);
    }
}
