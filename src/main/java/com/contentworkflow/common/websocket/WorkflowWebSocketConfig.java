package com.contentworkflow.common.websocket;

import com.contentworkflow.document.interfaces.ws.DocumentRealtimeWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：
 * 本项目实时协作采用“原生 WebSocket + 自定义 JSON 协议”，
 * 避免 STOMP 在高频小消息场景下的额外帧开销。
 */
@Configuration
@EnableWebSocket
public class WorkflowWebSocketConfig implements WebSocketConfigurer {

    private final DocumentRealtimeWebSocketHandler realtimeHandler;

    public WorkflowWebSocketConfig(DocumentRealtimeWebSocketHandler realtimeHandler) {
        this.realtimeHandler = realtimeHandler;
    }

    /**
     * 注册原生 WebSocket 端点。
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(realtimeHandler, "/ws/docs")
                .setAllowedOriginPatterns("*");
    }
}
