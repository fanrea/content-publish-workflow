package com.contentworkflow.document.application.gc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMqDocumentCompactionTaskConsumerTest {

    @Test
    void afterPropertiesSet_shouldConsumeAndExecuteTask() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        DocumentCompactionExecutor executor = mock(DocumentCompactionExecutor.class);
        when(consumer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");

        RocketMqDocumentCompactionTaskConsumer taskConsumer =
                new RocketMqDocumentCompactionTaskConsumer(objectMapper, executor, "cpw_doc_compaction_task", consumer);
        taskConsumer.afterPropertiesSet();

        ArgumentCaptor<MessageListenerOrderly> listenerCaptor = ArgumentCaptor.forClass(MessageListenerOrderly.class);
        verify(consumer, times(1)).registerMessageListener(listenerCaptor.capture());

        DocumentCompactionTask task = new DocumentCompactionTask(100L, "UPDATE_COUNT", Instant.parse("2026-01-01T00:00:00Z"));
        MessageExt message = new MessageExt();
        message.setBody(objectMapper.writeValueAsBytes(task));

        ConsumeOrderlyStatus status = listenerCaptor.getValue().consumeMessage(List.of(message), null);

        assertThat(status).isEqualTo(ConsumeOrderlyStatus.SUCCESS);
        verify(executor, times(1)).execute(task);
    }

    @Test
    void consumeOrderly_shouldIgnoreInvalidPayloadAndContinue() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        DocumentCompactionExecutor executor = mock(DocumentCompactionExecutor.class);
        when(consumer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");

        RocketMqDocumentCompactionTaskConsumer taskConsumer =
                new RocketMqDocumentCompactionTaskConsumer(objectMapper, executor, "cpw_doc_compaction_task", consumer);
        taskConsumer.afterPropertiesSet();

        ArgumentCaptor<MessageListenerOrderly> listenerCaptor = ArgumentCaptor.forClass(MessageListenerOrderly.class);
        verify(consumer, times(1)).registerMessageListener(listenerCaptor.capture());

        MessageExt badMessage = new MessageExt();
        badMessage.setBody("not-json".getBytes());
        badMessage.setTopic("cpw_doc_compaction_task");
        badMessage.setMsgId("msg-bad");

        ConsumeOrderlyStatus status = listenerCaptor.getValue().consumeMessage(List.of(badMessage), null);

        assertThat(status).isEqualTo(ConsumeOrderlyStatus.SUCCESS);
        verify(executor, never()).execute(any(DocumentCompactionTask.class));
    }

    @Test
    void consumeOrderly_shouldSuspendWhenExecuteFails() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        DocumentCompactionExecutor executor = mock(DocumentCompactionExecutor.class);
        when(consumer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        doThrow(new RuntimeException("execute failed")).when(executor).execute(any(DocumentCompactionTask.class));

        RocketMqDocumentCompactionTaskConsumer taskConsumer =
                new RocketMqDocumentCompactionTaskConsumer(objectMapper, executor, "cpw_doc_compaction_task", consumer);
        taskConsumer.afterPropertiesSet();

        ArgumentCaptor<MessageListenerOrderly> listenerCaptor = ArgumentCaptor.forClass(MessageListenerOrderly.class);
        verify(consumer, times(1)).registerMessageListener(listenerCaptor.capture());

        DocumentCompactionTask task = new DocumentCompactionTask(100L, "TIME_WINDOW", Instant.parse("2026-01-01T00:00:00Z"));
        MessageExt message = new MessageExt();
        message.setBody(objectMapper.writeValueAsBytes(task));
        message.setTopic("cpw_doc_compaction_task");
        message.setMsgId("msg-1");

        ConsumeOrderlyStatus status = listenerCaptor.getValue().consumeMessage(List.of(message), null);

        assertThat(status).isEqualTo(ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT);
    }

    @Test
    void consumeOrderly_shouldParseLegacyUpperClockFromTrigger() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        DocumentCompactionExecutor executor = mock(DocumentCompactionExecutor.class);
        when(consumer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");

        RocketMqDocumentCompactionTaskConsumer taskConsumer =
                new RocketMqDocumentCompactionTaskConsumer(objectMapper, executor, "cpw_doc_compaction_task", consumer);
        taskConsumer.afterPropertiesSet();

        ArgumentCaptor<MessageListenerOrderly> listenerCaptor = ArgumentCaptor.forClass(MessageListenerOrderly.class);
        verify(consumer, times(1)).registerMessageListener(listenerCaptor.capture());

        MessageExt legacyMessage = new MessageExt();
        legacyMessage.setBody("""
                {"documentId":100,"trigger":"TOMBSTONE_GC:upperClock=40","createdAt":"2026-01-01T00:00:00Z"}
                """.getBytes());
        legacyMessage.setTopic("cpw_doc_compaction_task");
        legacyMessage.setMsgId("msg-legacy");

        ConsumeOrderlyStatus status = listenerCaptor.getValue().consumeMessage(List.of(legacyMessage), null);

        assertThat(status).isEqualTo(ConsumeOrderlyStatus.SUCCESS);
        ArgumentCaptor<DocumentCompactionTask> taskCaptor = ArgumentCaptor.forClass(DocumentCompactionTask.class);
        verify(executor, times(1)).execute(taskCaptor.capture());
        assertThat(taskCaptor.getValue().segmentUpperClockInclusive()).isEqualTo(40L);
    }
}
