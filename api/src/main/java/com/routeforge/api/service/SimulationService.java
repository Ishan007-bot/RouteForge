package com.routeforge.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.routeforge.api.dto.SimControlDto;
import com.routeforge.api.dto.SimEventDto;
import com.routeforge.api.dto.SimScenarioDto;
import com.routeforge.api.dto.SimSpawnDto;
import com.routeforge.engine.sim.SimulationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Web-layer facade for {@link SimulationEngine}.
 *
 * <p>Holds no state of its own — it translates DTOs into engine commands.
 * The engine is the single source of truth.
 *
 * <p>Scenario loading uses Jackson's YAML factory to bind a {@link
 * SimScenarioDto} from an upload, then dispatches spawn and event
 * commands. Time-delayed events use a {@link Timer} so the scenario does
 * not block the controller thread.
 */
@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private final SimulationEngine engine;
    private final Timer scenarioTimer = new Timer("sim-scenario", true);

    public SimulationService(SimulationEngine engine) {
        this.engine = engine;
    }

    public SimulationEngine engine() { return engine; }

    /* -------------------- control -------------------- */

    public void control(SimControlDto dto) {
        if (dto.speedMultiplier() != null) {
            engine.setSpeedMultiplier(dto.speedMultiplier());
        }
        if (dto.action() != null) {
            switch (dto.action()) {
                case "play"  -> engine.start();
                case "pause" -> engine.pause();
                case "reset" -> engine.reset();
                default      -> throw new IllegalArgumentException(
                        "Unknown action: " + dto.action() + " (expected: play, pause, reset)");
            }
        }
    }

    /* -------------------- spawn -------------------- */

    public int spawn(SimSpawnDto dto) {
        int count = Math.max(1, dto.count());
        int firstId = -1;
        for (int i = 0; i < count; i++) {
            int id = engine.spawn(dto.fromLat(), dto.fromLon(), dto.toLat(), dto.toLon(), dto.profile());
            if (i == 0) firstId = id;
        }
        return firstId;
    }

    /* -------------------- events -------------------- */

    public int event(SimEventDto dto) {
        if (dto.edgeId() != null) {
            int e = dto.edgeId();
            switch (dto.type()) {
                case "close" -> engine.closeEdge(e);
                case "open"  -> engine.openEdge(e);
                default      -> throw new IllegalArgumentException("Unknown event type: " + dto.type());
            }
            return e;
        }
        return switch (dto.type()) {
            case "close" -> engine.closeNearestEdge(dto.lat(), dto.lon());
            case "open"  -> {
                int e = engine.pickNearestEdge(dto.lat(), dto.lon());
                if (e >= 0) engine.openEdge(e);
                yield e;
            }
            default -> throw new IllegalArgumentException("Unknown event type: " + dto.type());
        };
    }

    /* -------------------- scenarios -------------------- */

    public SimScenarioDto loadScenario(InputStream in) throws IOException {
        return YAML.readValue(in, SimScenarioDto.class);
    }

    /**
     * Apply a scenario: reset the engine, spawn all vehicles immediately,
     * schedule events for their {@code atSeconds} offsets, and start the clock.
     */
    public void applyScenario(SimScenarioDto scenario) {
        engine.reset();
        if (scenario.vehicles() != null) {
            for (var spec : scenario.vehicles()) {
                int n = spec.count() == null ? 1 : Math.max(1, spec.count());
                for (int i = 0; i < n; i++) {
                    engine.spawn(spec.fromLat(), spec.fromLon(),
                                 spec.toLat(),   spec.toLon(),
                                 spec.profile());
                }
            }
        }
        if (scenario.events() != null) {
            for (var ev : scenario.events()) {
                long delayMs = (long) (ev.atSeconds() * 1000);
                scenarioTimer.schedule(new TimerTask() {
                    @Override public void run() {
                        try {
                            event(new SimEventDto(ev.type(), ev.lat(), ev.lon(), null));
                        } catch (Exception ex) {
                            log.warn("Scenario event failed: {}", ex.toString());
                        }
                    }
                }, Math.max(0, delayMs));
            }
        }
        engine.start();
        log.info("Loaded scenario '{}': {} vehicle groups, {} events",
                scenario.name(),
                scenario.vehicles() == null ? 0 : scenario.vehicles().size(),
                scenario.events()   == null ? 0 : scenario.events().size());
    }
}
