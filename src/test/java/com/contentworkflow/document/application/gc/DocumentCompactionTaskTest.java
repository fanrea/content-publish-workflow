package com.contentworkflow.document.application.gc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentCompactionTaskTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldDeserializeLegacyTriggerUpperClockWhenStructuredFieldMissing() throws Exception {
        String legacyJson = """
                {
                  "documentId": 100,
                  "trigger": "TOMBSTONE_GC:upperClock=40",
                  "createdAt": "2026-01-01T00:00:00Z"
                }
                """;

        DocumentCompactionTask task = objectMapper.readValue(legacyJson, DocumentCompactionTask.class);

        assertThat(task.documentId()).isEqualTo(100L);
        assertThat(task.trigger()).isEqualTo("TOMBSTONE_GC:upperClock=40");
        assertThat(task.segmentUpperClockInclusive()).isEqualTo(40L);
    }

    @Test
    void shouldRetainStructuredUpperClockWhenProvided() throws Exception {
        DocumentCompactionTask task = new DocumentCompactionTask(
                100L,
                "TOMBSTONE_GC",
                Instant.parse("2026-01-01T00:00:00Z"),
                58L
        );

        String json = objectMapper.writeValueAsString(task);
        DocumentCompactionTask decoded = objectMapper.readValue(json, DocumentCompactionTask.class);

        assertThat(decoded.trigger()).isEqualTo("TOMBSTONE_GC");
        assertThat(decoded.segmentUpperClockInclusive()).isEqualTo(58L);
    }
}
