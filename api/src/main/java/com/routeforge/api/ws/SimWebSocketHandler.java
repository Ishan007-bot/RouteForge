package com.routeforge.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeforge.engine.sim.SimulationEngine;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Streams {@link SimulationEngine.Snapshot}s to every connected client.
 *
 * <p>Each {@code /ws/sim} websocket connection is registered in a
 * {@link CopyOnWriteArraySet} on open and removed on close. The handler
 * registers itself as a {@link SimulationEngine.Listener}; the engine
 * calls back on its tick thread, the handler serialises to JSON, and
 * each session gets the same payload.
 *
 * <p>Failing sends are silently dropped — the dead session is removed
 * the next time the socket layer notices it.
 */
@Component
public class SimWebSocketHandler extends TextWebSocketHandler implements SimulationEngine.Listener {

    private static final Logger log = LoggerFactory.getLogger(SimWebSocketHandler.class);

    private final SimulationEngine engine;
    private final ObjectMapper json = new ObjectMapper();
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    public SimWebSocketHandler(SimulationEngine engine) {
        this.engine = engine;
    }

    @PostConstruct
    void register() {
        engine.addListener(this);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("Sim websocket connected: {} (total: {})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("Sim websocket closed: {} ({}) (total: {})", session.getId(), status, sessions.size());
    }

    @Override
    public void onTick(SimulationEngine.Snapshot snapshot) {
        if (sessions.isEmpty()) return;
        String payload;
        try {
            payload = json.writeValueAsString(snapshot);
        } catch (IOException e) {
            log.warn("Snapshot serialise failed: {}", e.toString());
            return;
        }
        TextMessage msg = new TextMessage(payload);
        for (WebSocketSession s : sessions) {
            if (!s.isOpen()) { sessions.remove(s); continue; }
            try { s.sendMessage(msg); }
            catch (Exception e) { sessions.remove(s); }
        }
    }
}
