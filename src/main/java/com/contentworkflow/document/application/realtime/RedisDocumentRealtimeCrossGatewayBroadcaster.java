package com.contentworkflow.document.application.realtime;

import com.contentworkflow.document.interfaces.ws.DocumentWsEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(prefix = "workflow.realtime.redis-broadcast", name = "enabled", havingValue = "true")
public class RedisDocumentRealtimeCrossGatewayBroadcaster implements DocumentRealtimeCrossGatewayBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RedisDocumentRealtimeCrossGatewayBroadcaster.class);
    private static final Duration INBOUND_DEDUP_WINDOW = Duration.ofSeconds(5);
    private static final int MAX_INBOUND_DEDUP_ENTRIES = 4096;

    private final StringRedisTemplate redisTemplate;
    private final DocumentRealtimeSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final String gatewayId;
    private final String channelPrefix;
    private final Map<String, Instant> recentInboundPayloads = new ConcurrentHashMap<>();
    private volatile RedisMessageListenerContainer listenerContainer;

    public RedisDocumentRealtimeCrossGatewayBroadcaster(
            StringRedisTemplate redisTemplate,
            DocumentRealtimeSessionRegistry sessionRegistry,
            ObjectMapper objectMapper,
            @Value("${workflow.realtime.gateway-id:gateway-local}") String gatewayId,
            @Value("${workflow.realtime.redis-broadcast.channel:cpw:realtime:broadcast}") String channel) {
        this.redisTemplate = redisTemplate;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.gatewayId = gatewayId == null || gatewayId.isBlank() ? "gateway-local" : gatewayId.trim();
        this.channelPrefix = channel == null || channel.isBlank() ? "cpw:realtime:broadcast" : channel.trim();
    }

    @Override
    public void publish(DocumentWsEvent event) {
        if (event == null || event.docId() == null || event.docId() <= 0) {
            return;
        }
        Set<String> targetGateways = loadTargetGateways(event.docId());
        if (targetGateways.isEmpty()) {
            log.debug("redis cross-gateway publish skipped due to empty room route, docId={}, sourceGatewayId={}",
                    event.docId(),
                    gatewayId);
            return;
        }
        DocumentRealtimeCrossGatewayEnvelope envelope =
                new DocumentRealtimeCrossGatewayEnvelope(gatewayId, event.docId(), event);
        try {
            String payload = objectMapper.writeValueAsString(envelope);
            for (String targetGateway : targetGateways) {
                redisTemplate.convertAndSend(channelOf(targetGateway), payload);
            }
        } catch (Exception ex) {
            log.warn("redis cross-gateway publish failed, channelPrefix={}, docId={}, eventType={}",
                    channelPrefix,
                    event.docId(),
                    event.type(),
                    ex);
        }
    }

    @PostConstruct
    void startListener() {
        if (redisTemplate.getConnectionFactory() == null) {
            log.warn("redis cross-gateway listener skipped, redis connection factory is null");
            return;
        }
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisTemplate.getConnectionFactory());
        container.setErrorHandler(ex -> log.warn("redis cross-gateway listener error", ex));
        MessageListener listener = this::handleMessage;
        String localChannel = localGatewayChannel();
        container.addMessageListener(listener, ChannelTopic.of(localChannel));
        try {
            container.afterPropertiesSet();
            container.start();
            listenerContainer = container;
            log.info("redis cross-gateway listener started, gatewayId={}, channel={}", gatewayId, localChannel);
        } catch (Exception ex) {
            log.warn("redis cross-gateway listener start failed, fallback to local-only broadcast", ex);
            try {
                container.stop();
            } catch (Exception stopEx) {
                log.debug("redis cross-gateway listener stop after failed start ignored", stopEx);
            }
        }
    }

    @PreDestroy
    void stopListener() {
        RedisMessageListenerContainer container = listenerContainer;
        if (container == null) {
            return;
        }
        try {
            container.stop();
        } catch (Exception ex) {
            log.debug("redis cross-gateway listener stop failed", ex);
        }
    }

    void onBroadcastPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        if (shouldSkipDuplicateInboundPayload(payload)) {
            return;
        }
        try {
            DocumentRealtimeCrossGatewayEnvelope envelope =
                    objectMapper.readValue(payload, DocumentRealtimeCrossGatewayEnvelope.class);
            if (envelope == null || envelope.documentId() == null || envelope.documentId() <= 0 || envelope.event() == null) {
                return;
            }
            if (gatewayId.equals(envelope.sourceGatewayId())) {
                return;
            }
            DocumentWsEvent event = normalizePresence(envelope.documentId(), envelope.event());
            broadcastLocal(envelope.documentId(), event);
        } catch (Exception ex) {
            log.warn("redis cross-gateway payload decode failed, channelPrefix={}, payload={}", channelPrefix, payload, ex);
        }
    }

    private void handleMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null || message.getBody().length == 0) {
            return;
        }
        onBroadcastPayload(new String(message.getBody(), StandardCharsets.UTF_8));
    }

    private DocumentWsEvent normalizePresence(Long documentId, DocumentWsEvent event) {
        if (!"PRESENCE".equals(event.type())) {
            return event;
        }
        List<String> participants = loadOnlineParticipants(documentId);
        if (participants.isEmpty()) {
            participants = event.participants();
        }
        return DocumentWsEvent.presence(documentId, participants, event.message());
    }

    private List<String> loadOnlineParticipants(Long documentId) {
        try {
            HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
            Map<Object, Object> entries = hashOperations.entries("doc:" + documentId + ":online_users");
            if (entries == null || entries.isEmpty()) {
                return List.of();
            }
            return entries.keySet().stream()
                    .map(Object::toString)
                    .filter(value -> value != null && !value.isBlank())
                    .sorted()
                    .toList();
        } catch (Exception ex) {
            log.warn("redis cross-gateway load online users failed, docId={}", documentId, ex);
            return List.of();
        }
    }

    private Set<String> loadTargetGateways(Long documentId) {
        try {
            SetOperations<String, String> setOperations = redisTemplate.opsForSet();
            Set<String> gateways = setOperations.members(roomGatewaysKey(documentId));
            if (gateways == null || gateways.isEmpty()) {
                return Set.of();
            }
            LinkedHashSet<String> targets = new LinkedHashSet<>();
            for (String gateway : gateways) {
                if (gateway == null || gateway.isBlank()) {
                    continue;
                }
                String normalized = gateway.trim();
                if (gatewayId.equals(normalized)) {
                    continue;
                }
                targets.add(normalized);
            }
            return targets;
        } catch (Exception ex) {
            log.warn("redis cross-gateway load room route failed, docId={}", documentId, ex);
            return Set.of();
        }
    }

    private String roomGatewaysKey(Long documentId) {
        return "doc:" + documentId + ":room_gateways";
    }

    private String localGatewayChannel() {
        return channelOf(gatewayId);
    }

    private String channelOf(String targetGatewayId) {
        return channelPrefix + ":" + targetGatewayId;
    }

    private void broadcastLocal(Long documentId, DocumentWsEvent event) {
        for (WebSocketSession session : sessionRegistry.sessionsOf(documentId)) {
            sendSafe(session, event);
        }
    }

    private boolean shouldSkipDuplicateInboundPayload(String payload) {
        Instant now = Instant.now();
        String key = inboundPayloadKey(payload);
        Instant previous = recentInboundPayloads.put(key, now);
        pruneRecentInboundPayloads(now);
        return previous != null && Duration.between(previous, now).compareTo(INBOUND_DEDUP_WINDOW) < 0;
    }

    private String inboundPayloadKey(String payload) {
        return payload.length() + ":" + Integer.toHexString(payload.hashCode());
    }

    private void pruneRecentInboundPayloads(Instant now) {
        if (recentInboundPayloads.size() <= MAX_INBOUND_DEDUP_ENTRIES) {
            return;
        }
        Instant cutoff = now.minus(INBOUND_DEDUP_WINDOW);
        Iterator<Map.Entry<String, Instant>> iterator = recentInboundPayloads.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();
            if (entry.getValue().isBefore(cutoff)) {
                iterator.remove();
            }
        }
    }

    private void sendSafe(WebSocketSession session, DocumentWsEvent event) {
        WebSocketSession target = sessionRegistry.resolveSendSession(session);
        if (target == null || !target.isOpen()) {
            return;
        }
        try {
            target.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
        } catch (IOException ex) {
            log.warn("websocket push failed for cross-gateway event, sessionId={}, eventType={}",
                    target.getId(),
                    event.type(),
                    ex);
        }
    }
}
