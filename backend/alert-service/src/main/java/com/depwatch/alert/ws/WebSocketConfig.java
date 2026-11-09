package com.depwatch.alert.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket。
 *
 * 端点 /ws 由网关代理（Spring Cloud Gateway 原生支持 WS 升级），JWT 在握手请求的 ?token= 里，
 * 网关校验后才转发到这里。所以本服务不再重复鉴权。
 *
 * 主题：
 *   /topic/scans/{scanId}        扫描进度（任何知道 scanId 的人都能订阅——scanId 是随机 UUID，可视为能力凭证）
 *   /topic/users/{userId}/alerts  用户告警
 *
 * 用的是内存 SimpleBroker：多实例部署时各实例只知道自己连接上的客户端，
 * 生产环境换成 enableStompBrokerRelay() 接 RabbitMQ/ActiveMQ，或者用 Redis Pub/Sub 做扇出。
 * 这是单机 → 多机的经典取舍，面试可讲。
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
