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
        ObjectMapper objectMapper = new ObjectMapper();
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        DocumentOperationIngressHandler ingressHandler = mock(DocumentOperationIngressHandler.class);
        when(consumer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");

        RocketMqDocumentOperationIngressConsumer ingressConsumer =
                new RocketMqDocumentOperationIngressConsumer(objectMapper, ingressHandler, "cpw_doc_ingress", consumer);
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
        ObjectMapper objectMapper = new ObjectMapper();
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        DocumentOperationIngressHandler ingressHandler = mock(DocumentOperationIngressHandler.class);
        when(consumer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        doThrow(new RuntimeException("handler failure")).when(ingressHandler).handle(any(DocumentOperationIngressCommand.class));

        RocketMqDocumentOperationIngressConsumer ingressConsumer =
                new RocketMqDocumentOperationIngressConsumer(objectMapper, ingressHandler, "cpw_doc_ingress", consumer);
        ingressConsumer.afterPropertiesSet();

        ArgumentCaptor<MessageListenerOrderly> listenerCaptor = ArgumentCaptor.forClass(MessageListenerOrderly.class);
        verify(consumer, times(1)).registerMessageListener(listenerCaptor.capture());

        DocumentOperationIngressCommand command = buildCommand(33002L, 6L);
        MessageExt message = new MessageExt();
        message.setBody(objectMapper.writeValueAsBytes(command));
        message.setTopic("cpw_doc_ingress");
        message.setMsgId("msg-1");

        ConsumeOrderlyStatus status = listenerCaptor.getValue().consumeMessage(List.of(message), null);

        assertThat(status).isEqualTo(ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT);
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
}
