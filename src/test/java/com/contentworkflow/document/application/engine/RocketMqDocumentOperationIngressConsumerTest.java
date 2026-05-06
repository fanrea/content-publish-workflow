package com.contentworkflow.document.application.engine;

import com.contentworkflow.document.application.ingress.DocumentOperationIngressCommand;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMqDocumentOperationIngressConsumerTest {

    @Test
    void afterPropertiesSet_shouldConsumeOrderlyAndDelegateToHandler() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        DocumentOperationIngressHandler ingressHandler = mock(DocumentOperationIngressHandler.class);
        IngressRepairTaskStore repairTaskStore = mock(IngressRepairTaskStore.class);
        when(consumer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");

        RocketMqDocumentOperationIngressConsumer ingressConsumer =
                new RocketMqDocumentOperationIngressConsumer(objectMapper, ingressHandler, repairTaskStore, "cpw_doc_ingress", 16, consumer);
        ingressConsumer.afterPropertiesSet();

        ArgumentCaptor<MessageListenerOrderly> listenerCaptor = ArgumentCaptor.forClass(MessageListenerOrderly.class);
        verify(consumer, times(1)).registerMessageListener(listenerCaptor.capture());

        DocumentOperationIngressCommand command = buildCommand(33001L, 5L);
        MessageExt message = new MessageExt();
        message.setBody(objectMapper.writeValueAsBytes(command));

        ConsumeOrderlyStatus status = listenerCaptor.getValue().consumeMessage(List.of(message), null);

        assertThat(status).isEqualTo(ConsumeOrderlyStatus.SUCCESS);
        ArgumentCaptor<DocumentOperationIngressCommand> commandCaptor =
                ArgumentCaptor.forClass(DocumentOperationIngressCommand.class);
        verify(ingressHandler, times(1)).handle(commandCaptor.capture());
        assertThat(commandCaptor.getValue().docId()).isEqualTo(33001L);
    }

    @Test
    void consumeOrderly_shouldSuspendCurrentQueueWhenHandlerFails() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        DocumentOperationIngressHandler ingressHandler = mock(DocumentOperationIngressHandler.class);
        IngressRepairTaskStore repairTaskStore = mock(IngressRepairTaskStore.class);
        when(consumer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        doThrow(new RuntimeException("handler failure")).when(ingressHandler).handle(any(DocumentOperationIngressCommand.class));

        RocketMqDocumentOperationIngressConsumer ingressConsumer =
                new RocketMqDocumentOperationIngressConsumer(objectMapper, ingressHandler, repairTaskStore, "cpw_doc_ingress", 2, consumer);
        ingressConsumer.afterPropertiesSet();

        ArgumentCaptor<MessageListenerOrderly> listenerCaptor = ArgumentCaptor.forClass(MessageListenerOrderly.class);
        verify(consumer, times(1)).registerMessageListener(listenerCaptor.capture());

        DocumentOperationIngressCommand command = buildCommand(33002L, 6L);
        MessageExt message = new MessageExt();
        message.setBody(objectMapper.writeValueAsBytes(command));
        message.setTopic("cpw_doc_ingress");
        message.setMsgId("msg-1");
        message.setReconsumeTimes(1);

        ConsumeOrderlyStatus status = listenerCaptor.getValue().consumeMessage(List.of(message), null);

        assertThat(status).isEqualTo(ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT);
        verify(repairTaskStore, times(0)).saveOrUpdate(any(IngressRepairTask.class));
    }

    @Test
    void consumeOrderly_shouldPersistRepairTaskAndAckWhenRetryExceeded() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        DocumentOperationIngressHandler ingressHandler = mock(DocumentOperationIngressHandler.class);
        IngressRepairTaskStore repairTaskStore = mock(IngressRepairTaskStore.class);
        when(consumer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        doThrow(new RuntimeException("handler failure")).when(ingressHandler).handle(any(DocumentOperationIngressCommand.class));

        RocketMqDocumentOperationIngressConsumer ingressConsumer =
                new RocketMqDocumentOperationIngressConsumer(objectMapper, ingressHandler, repairTaskStore, "cpw_doc_ingress", 2, consumer);
        ingressConsumer.afterPropertiesSet();

        ArgumentCaptor<MessageListenerOrderly> listenerCaptor = ArgumentCaptor.forClass(MessageListenerOrderly.class);
        verify(consumer, times(1)).registerMessageListener(listenerCaptor.capture());

        DocumentOperationIngressCommand command = buildCommand(33003L, 7L);
        byte[] body = objectMapper.writeValueAsBytes(command);
        MessageExt message = new MessageExt();
        message.setBody(body);
        message.setTopic("cpw_doc_ingress");
        message.setMsgId("msg-2");
        message.setReconsumeTimes(2);

        ConsumeOrderlyStatus status = listenerCaptor.getValue().consumeMessage(List.of(message), null);

        assertThat(status).isEqualTo(ConsumeOrderlyStatus.SUCCESS);
        ArgumentCaptor<IngressRepairTask> taskCaptor = ArgumentCaptor.forClass(IngressRepairTask.class);
        verify(repairTaskStore, times(1)).saveOrUpdate(taskCaptor.capture());
        assertThat(taskCaptor.getValue().docId()).isEqualTo(33003L);
        assertThat(taskCaptor.getValue().sessionId()).isEqualTo("session-1");
        assertThat(taskCaptor.getValue().clientSeq()).isEqualTo(7L);
        assertThat(taskCaptor.getValue().retryCount()).isEqualTo(2);
        assertThat(taskCaptor.getValue().status()).isEqualTo("PENDING");
        assertThat(taskCaptor.getValue().payload()).isEqualTo(new String(body, StandardCharsets.UTF_8));
    }

    @Test
    void consumeOrderly_shouldSuspendWhenRepairTaskPersistFails() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        DocumentOperationIngressHandler ingressHandler = mock(DocumentOperationIngressHandler.class);
        IngressRepairTaskStore repairTaskStore = mock(IngressRepairTaskStore.class);
        when(consumer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        doThrow(new RuntimeException("handler failure")).when(ingressHandler).handle(any(DocumentOperationIngressCommand.class));
        doThrow(new RuntimeException("db down")).when(repairTaskStore).saveOrUpdate(any(IngressRepairTask.class));

        RocketMqDocumentOperationIngressConsumer ingressConsumer =
                new RocketMqDocumentOperationIngressConsumer(objectMapper, ingressHandler, repairTaskStore, "cpw_doc_ingress", 2, consumer);
        ingressConsumer.afterPropertiesSet();

        ArgumentCaptor<MessageListenerOrderly> listenerCaptor = ArgumentCaptor.forClass(MessageListenerOrderly.class);
        verify(consumer, times(1)).registerMessageListener(listenerCaptor.capture());

        DocumentOperationIngressCommand command = buildCommand(33004L, 8L);
        MessageExt message = new MessageExt();
        message.setBody(objectMapper.writeValueAsBytes(command));
        message.setTopic("cpw_doc_ingress");
        message.setMsgId("msg-3");
        message.setReconsumeTimes(2);

        ConsumeOrderlyStatus status = listenerCaptor.getValue().consumeMessage(List.of(message), null);

        assertThat(status).isEqualTo(ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT);
        verify(repairTaskStore, times(1)).saveOrUpdate(any(IngressRepairTask.class));
    }

    private DocumentOperationIngressCommand buildCommand(Long docId, Long clientSeq) {
        DocumentWsOperation operation = new DocumentWsOperation();
        operation.setOpType(DocumentOpType.INSERT);
        operation.setPosition(0);
        operation.setLength(0);
        operation.setText("X");
        return new DocumentOperationIngressCommand(
                docId,
                1,
                "session-1",
                clientSeq,
                "editor-1",
                "editor-1",
                operation,
                LocalDateTime.now()
        );
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
