package org.example.gamecenter.config;

import org.example.gamecenter.service.IGameService;
import org.example.gamecenter.websocket.WebSocket;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class WebSocketConfig {

    @Bean
    public Object initWebSocketDependencies(
            IGameService gameService,
            RedisTemplate<String, Object> redisTemplate) {
        // 初始化WebSocket的静态依赖
        WebSocket.initDependencies(gameService, redisTemplate);
        return new Object();
    }
}
