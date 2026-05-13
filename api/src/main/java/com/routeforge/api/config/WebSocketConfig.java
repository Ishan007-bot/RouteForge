package com.routeforge.api.config;

import com.routeforge.api.ws.SimWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

/**
 * Registers the simulation websocket at {@code /ws/sim}.
 *
 * <p>Using a plain {@code WebSocketHandler} (not STOMP) keeps the client
 * minimal: the frontend opens a {@code WebSocket}, parses JSON, and
 * renders. No subscription protocol, no broker.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SimWebSocketHandler simHandler;
    private final List<String> allowedOrigins;

    public WebSocketConfig(
            SimWebSocketHandler simHandler,
            @Value("${routeforge.cors.allowed-origins:*}") List<String> allowedOrigins
    ) {
        this.simHandler = simHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(simHandler, "/ws/sim")
                .setAllowedOrigins(allowedOrigins.toArray(new String[0]));
    }
}
