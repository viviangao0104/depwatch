package com.depwatch.alert.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket。
 *
  * The /ws endpoint is proxied by the gateway (Spring Cloud Gateway supports the WS upgrade natively). The JWT travels
  * in the handshake's ?token= and the gateway verifies it before forwarding, so this service doesn't re-authenticate.
 *
  * Topics:
  *   /topic/scans/{scanId}        scan progress (anyone who knows the scanId can subscribe — it's a random UUID, treat it as a capability)
  *   /topic/users/{userId}/alerts  per-user alerts
 *
  * Uses the in-memory SimpleBroker: with multiple replicas each one only knows its own connected clients.
  * In production switch to enableStompBrokerRelay() with RabbitMQ/ActiveMQ, or fan out via Redis Pub/Sub.
  * Classic single-node → multi-node trade-off; good interview material.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
