package com.contentworkflow.document.application.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "workflow.realtime.redis-index", name = "enabled", havingValue = "true")
public class RedisDocumentRealtimeRedisIndex implements DocumentRealtimeRedisIndex {

    private static final Logger log = LoggerFactory.getLogger(RedisDocumentRealtimeRedisIndex.class);

    private final StringRedisTemplate redisTemplate;
    private final String gatewayId;

    public RedisDocumentRealtimeRedisIndex(StringRedisTemplate redisTemplate,
                                           @Value("${workflow.realtime.gateway-id:}") String configuredGatewayId) {
        this.redisTemplate = redisTemplate;
        this.gatewayId = resolveGatewayId(configuredGatewayId);
        log.info("realtime redis index enabled, gatewayId={}", this.gatewayId);
    }

    @Override
    public void addRoomGateway(Long documentId) {
        if (!isValidDocumentId(documentId)) {
            return;
        }
        try {
            redisTemplate.opsForSet().add(roomGatewaysKey(documentId), gatewayId);
        } catch (Exception ex) {
            log.warn("realtime redis index add room gateway failed, docId={}, gatewayId={}", documentId, gatewayId, ex);
        }
    }

    @Override
    public void removeRoomGateway(Long documentId) {
        if (!isValidDocumentId(documentId)) {
            return;
        }
        try {
            redisTemplate.opsForSet().remove(roomGatewaysKey(documentId), gatewayId);
        } catch (Exception ex) {
            log.warn("realtime redis index remove room gateway failed, docId={}, gatewayId={}", documentId, gatewayId, ex);
        }
    }

    @Override
    public void addGatewaySession(String sessionId) {
        if (isBlank(sessionId)) {
            return;
        }
        try {
            redisTemplate.opsForSet().add(gatewaySessionsKey(), sessionId);
        } catch (Exception ex) {
            log.warn("realtime redis index add gateway session failed, gatewayId={}, sessionId={}", gatewayId, sessionId, ex);
        }
    }

    @Override
    public void removeGatewaySession(String sessionId) {
        if (isBlank(sessionId)) {
            return;
        }
        try {
            redisTemplate.opsForSet().remove(gatewaySessionsKey(), sessionId);
        } catch (Exception ex) {
            log.warn("realtime redis index remove gateway session failed, gatewayId={}, sessionId={}", gatewayId, sessionId, ex);
        }
    }

    @Override
    public void incrementOnlineUser(Long documentId, String userId) {
        if (!isValidDocumentId(documentId) || isBlank(userId)) {
            return;
        }
        try {
            redisTemplate.opsForHash().increment(onlineUsersKey(documentId), userId, 1L);
        } catch (Exception ex) {
            log.warn("realtime redis index increment online user failed, docId={}, userId={}", documentId, userId, ex);
        }
    }

    @Override
    public void decrementOnlineUser(Long documentId, String userId) {
        if (!isValidDocumentId(documentId) || isBlank(userId)) {
            return;
        }
        try {
            Long remaining = redisTemplate.opsForHash().increment(onlineUsersKey(documentId), userId, -1L);
            if (remaining != null && remaining <= 0L) {
                redisTemplate.opsForHash().delete(onlineUsersKey(documentId), userId);
            }
        } catch (Exception ex) {
            log.warn("realtime redis index decrement online user failed, docId={}, userId={}", documentId, userId, ex);
        }
    }

    @Override
    public void upsertSessionClock(Long documentId, String sessionId, long clock) {
        if (!isValidDocumentId(documentId) || isBlank(sessionId) || clock <= 0L) {
            return;
        }
        try {
            redisTemplate.opsForHash().put(sessionClocksKey(documentId), sessionId, Long.toString(clock));
        } catch (Exception ex) {
            log.warn("realtime redis index upsert session clock failed, docId={}, sessionId={}, clock={}",
                    documentId,
                    sessionId,
                    clock,
                    ex);
        }
    }

    @Override
    public void removeSessionClock(Long documentId, String sessionId) {
        if (!isValidDocumentId(documentId) || isBlank(sessionId)) {
            return;
        }
        try {
            redisTemplate.opsForHash().delete(sessionClocksKey(documentId), sessionId);
        } catch (Exception ex) {
            log.warn("realtime redis index remove session clock failed, docId={}, sessionId={}",
                    documentId,
                    sessionId,
                    ex);
        }
    }

    @Override
    public OptionalLong minimumSessionClock(Long documentId) {
        if (!isValidDocumentId(documentId)) {
            return OptionalLong.empty();
        }
        try {
            HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
            Map<Object, Object> entries = hashOperations.entries(sessionClocksKey(documentId));
            if (entries == null || entries.isEmpty()) {
                return OptionalLong.empty();
            }
            OptionalLong min = entries.values().stream()
                    .filter(value -> value != null)
                    .map(Object::toString)
                    .map(this::parsePositiveLong)
                    .filter(OptionalLong::isPresent)
                    .mapToLong(OptionalLong::getAsLong)
                    .min();
            return min.isPresent() ? min : OptionalLong.empty();
        } catch (Exception ex) {
            log.warn("realtime redis index minimum session clock failed, docId={}", documentId, ex);
            return OptionalLong.empty();
        }
    }

    private String roomGatewaysKey(Long documentId) {
        return "doc:" + documentId + ":room_gateways";
    }

    private String onlineUsersKey(Long documentId) {
        return "doc:" + documentId + ":online_users";
    }

    private String sessionClocksKey(Long documentId) {
        return "doc:" + documentId + ":session_clocks";
    }

    private String gatewaySessionsKey() {
        return "gateway:" + gatewayId + ":sessions";
    }

    private String resolveGatewayId(String configuredGatewayId) {
        if (!isBlank(configuredGatewayId)) {
            return configuredGatewayId.trim();
        }
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            if (!isBlank(hostName)) {
                return hostName;
            }
        } catch (Exception ex) {
            log.warn("resolve gateway id from hostname failed, fallback to random id", ex);
        }
        return "gw-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private boolean isValidDocumentId(Long documentId) {
        return documentId != null && documentId > 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private OptionalLong parsePositiveLong(String value) {
        if (isBlank(value)) {
            return OptionalLong.empty();
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0L) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(parsed);
        } catch (NumberFormatException ex) {
            return OptionalLong.empty();
        }
    }
}
