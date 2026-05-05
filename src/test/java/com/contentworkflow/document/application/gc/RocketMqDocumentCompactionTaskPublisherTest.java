package com.contentworkflow.document.application.gc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMqDocumentCompactionTaskPublisherTest {

    @Test
    void publish_shouldSendMessageWhenStarted() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getSendStatus()).thenReturn(SendStatus.SEND_OK);
        when(producer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        when(producer.send(any(Message.class))).thenReturn(sendResult);

        RocketMqDocumentCompactionTaskPublisher publisher =
                new RocketMqDocumentCompactionTaskPublisher(objectMapper, producer, "cpw_doc_compaction_task", 0, 0);
        publisher.afterPropertiesSet();

        DocumentCompactionTask task = new DocumentCompactionTask(
                100L,
                "UPDATE_COUNT",
                Instant.parse("2026-01-01T00:00:00Z")
        );
        publisher.publish(task);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(producer, times(1)).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getTopic()).isEqualTo("cpw_doc_compaction_task");
        assertThat(messageCaptor.getValue().getTags()).isEqualTo("DOCUMENT_COMPACTION_TASK");
        assertThat(messageCaptor.getValue().getKeys()).isEqualTo("100:UPDATE_COUNT:1767225600000");
        DocumentCompactionTask payload = objectMapper.readValue(messageCaptor.getValue().getBody(), DocumentCompactionTask.class);
        assertThat(payload.documentId()).isEqualTo(100L);
        assertThat(payload.trigger()).isEqualTo("UPDATE_COUNT");
        assertThat(payload.segmentUpperClockInclusive()).isNull();
        assertThat(payload.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void publish_shouldIncludeUpperClockInMessageKeyWhenPresent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getSendStatus()).thenReturn(SendStatus.SEND_OK);
        when(producer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        when(producer.send(any(Message.class))).thenReturn(sendResult);

        RocketMqDocumentCompactionTaskPublisher publisher =
                new RocketMqDocumentCompactionTaskPublisher(objectMapper, producer, "cpw_doc_compaction_task", 0, 0);
        publisher.afterPropertiesSet();

        DocumentCompactionTask task = new DocumentCompactionTask(
                100L,
                "TOMBSTONE_GC",
                Instant.parse("2026-01-01T00:00:00Z"),
                58L
        );
        publisher.publish(task);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(producer, times(1)).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getKeys()).isEqualTo("100:TOMBSTONE_GC:1767225600000:58");
    }

    @Test
    void publish_shouldFailWhenProducerNotStarted() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        RocketMqDocumentCompactionTaskPublisher publisher =
                new RocketMqDocumentCompactionTaskPublisher(objectMapper, producer, "cpw_doc_compaction_task", 0, 0);

        assertThatThrownBy(() -> publisher.publish(new DocumentCompactionTask(100L, "UPDATE_COUNT", Instant.now())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("producer is not started");

        verify(producer, never()).send(any(Message.class));
    }

    @Test
    void publish_shouldRetryAndThrowWhenSendFails() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        when(producer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        when(producer.send(any(Message.class))).thenThrow(new RuntimeException("mq send failure"));

        RocketMqDocumentCompactionTaskPublisher publisher =
                new RocketMqDocumentCompactionTaskPublisher(objectMapper, producer, "cpw_doc_compaction_task", 2, 0);
        publisher.afterPropertiesSet();

        assertThatThrownBy(() -> publisher.publish(new DocumentCompactionTask(
                100L,
                "TIME_WINDOW",
                Instant.parse("2026-01-01T00:00:00Z")
        )))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("mq send failure");

        verify(producer, times(3)).send(any(Message.class));
    }
}
