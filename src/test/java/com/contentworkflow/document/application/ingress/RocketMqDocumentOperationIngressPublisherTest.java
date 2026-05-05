package com.contentworkflow.document.application.ingress;

import com.contentworkflow.document.interfaces.ws.DocumentWsOperation;
import com.contentworkflow.document.domain.enums.DocumentOpType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMqDocumentOperationIngressPublisherTest {

    @Test
    void publish_shouldFailFastWhenProducerNotStarted() {
        ObjectMapper objectMapper = new ObjectMapper();
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        RocketMqDocumentOperationIngressPublisher publisher =
                new RocketMqDocumentOperationIngressPublisher(objectMapper, producer, "cpw_doc_ingress", 0, 0);

        assertThatThrownBy(() -> publisher.publish(buildCommand(100L, 1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("producer is not started");

        verify(producer, never()).send(any(Message.class), any(MessageQueueSelector.class), any());
    }

    @Test
    void publish_shouldRejectNullCommand() {
        ObjectMapper objectMapper = new ObjectMapper();
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        RocketMqDocumentOperationIngressPublisher publisher =
                new RocketMqDocumentOperationIngressPublisher(objectMapper, producer, "cpw_doc_ingress", 0, 0);

        assertThatThrownBy(() -> publisher.publish(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("command must not be null");
    }

    @Test
    void publish_shouldRejectNullDocId() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        when(producer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        RocketMqDocumentOperationIngressPublisher publisher =
                new RocketMqDocumentOperationIngressPublisher(objectMapper, producer, "cpw_doc_ingress", 0, 0);
        publisher.afterPropertiesSet();

        assertThatThrownBy(() -> publisher.publish(buildCommand(null, 1L)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("command.docId must not be null");
    }

    @Test
    void publish_shouldRouteByDocIdToStableQueue() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getSendStatus()).thenReturn(SendStatus.SEND_OK);
        when(producer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        when(producer.send(any(Message.class), any(MessageQueueSelector.class), any())).thenReturn(sendResult);

        RocketMqDocumentOperationIngressPublisher publisher =
                new RocketMqDocumentOperationIngressPublisher(objectMapper, producer, "cpw_doc_ingress", 0, 0);
        publisher.afterPropertiesSet();

        DocumentOperationIngressCommand command = buildCommand(10086L, 1L);
        publisher.publish(command);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<MessageQueueSelector> selectorCaptor = ArgumentCaptor.forClass(MessageQueueSelector.class);
        ArgumentCaptor<Object> docIdCaptor = ArgumentCaptor.forClass(Object.class);
        verify(producer, times(1)).send(messageCaptor.capture(), selectorCaptor.capture(), docIdCaptor.capture());

        assertThat(docIdCaptor.getValue()).isEqualTo(command.docId());
        assertThat(messageCaptor.getValue().getKeys()).isEqualTo(command.docId() + ":" + command.sessionId() + ":" + command.clientSeq());

        List<MessageQueue> queues = List.of(
                new MessageQueue("cpw_doc_ingress", "broker-a", 0),
                new MessageQueue("cpw_doc_ingress", "broker-a", 1),
                new MessageQueue("cpw_doc_ingress", "broker-a", 2),
                new MessageQueue("cpw_doc_ingress", "broker-a", 3)
        );
        MessageQueue selectedQueue = selectorCaptor.getValue().select(queues, messageCaptor.getValue(), command.docId());

        int expectedQueueIndex = RocketMqDocumentOperationIngressPublisher.resolveQueueIndex(command.docId(), queues.size());
        assertThat(selectedQueue.getQueueId()).isEqualTo(expectedQueueIndex);
    }

    @Test
    void publish_shouldKeepRetryAndThrowWhenSendFails() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        when(producer.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        when(producer.send(any(Message.class), any(MessageQueueSelector.class), any()))
                .thenThrow(new RuntimeException("mq send failure"));

        RocketMqDocumentOperationIngressPublisher publisher =
                new RocketMqDocumentOperationIngressPublisher(objectMapper, producer, "cpw_doc_ingress", 2, 0);
        publisher.afterPropertiesSet();

        assertThatThrownBy(() -> publisher.publish(buildCommand(20001L, 2L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("mq send failure");

        verify(producer, times(3)).send(any(Message.class), any(MessageQueueSelector.class), eq(20001L));
    }

    @Test
    void resolveQueueIndex_shouldStayStableForHashCollisionAndBoundaryDocIds() {
        int queueCount = 17;

        Long collisionDocIdA = 4_294_967_295L;      // 0x00000000FFFFFFFF
        Long collisionDocIdB = -4_294_967_296L;     // 0xFFFFFFFF00000000
        assertThat(collisionDocIdA.hashCode()).isEqualTo(collisionDocIdB.hashCode());

        int collisionIndexA = RocketMqDocumentOperationIngressPublisher.resolveQueueIndex(collisionDocIdA, queueCount);
        int collisionIndexB = RocketMqDocumentOperationIngressPublisher.resolveQueueIndex(collisionDocIdB, queueCount);
        assertThat(collisionIndexA).isEqualTo(collisionIndexB);
        assertThat(collisionIndexA).isBetween(0, queueCount - 1);

        int negativeDocIdIndex = RocketMqDocumentOperationIngressPublisher.resolveQueueIndex(-1L, queueCount);
        assertThat(negativeDocIdIndex).isBetween(0, queueCount - 1);
        assertThat(RocketMqDocumentOperationIngressPublisher.resolveQueueIndex(-1L, queueCount)).isEqualTo(negativeDocIdIndex);

        int largeDocIdIndex = RocketMqDocumentOperationIngressPublisher.resolveQueueIndex(Long.MAX_VALUE, queueCount);
        assertThat(largeDocIdIndex).isBetween(0, queueCount - 1);
        assertThat(RocketMqDocumentOperationIngressPublisher.resolveQueueIndex(Long.MAX_VALUE, queueCount)).isEqualTo(largeDocIdIndex);
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
