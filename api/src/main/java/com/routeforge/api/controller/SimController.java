package com.routeforge.api.controller;

import com.routeforge.api.dto.SimControlDto;
import com.routeforge.api.dto.SimEventDto;
import com.routeforge.api.dto.SimScenarioDto;
import com.routeforge.api.dto.SimSpawnDto;
import com.routeforge.api.service.SimulationService;
import com.routeforge.engine.sim.SimulationEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Control surface for the live simulation.
 *
 * <p>Side-effects are dispatched through {@link SimulationService} which
 * posts them to the engine's tick thread, so a controller call returns
 * before the change is observable in snapshots. Clients watch the
 * {@code /ws/sim} websocket for the resulting state.
 */
@RestController
@RequestMapping("/api/sim")
@Tag(name = "Simulator", description = "Live traffic simulation control plane.")
public class SimController {

    private final SimulationService service;

    public SimController(SimulationService service) {
        this.service = service;
    }

    @PostMapping(path = "/control", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Play, pause, reset, or change speed.")
    public ResponseEntity<Map<String, Object>> control(@Valid @RequestBody SimControlDto dto) {
        service.control(dto);
        return ResponseEntity.ok(state());
    }

    @PostMapping(path = "/spawn", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Spawn one or more vehicles on the same trip.")
    public ResponseEntity<Map<String, Object>> spawn(@Valid @RequestBody SimSpawnDto dto) {
        int firstId = service.spawn(dto);
        return ResponseEntity.ok(Map.of("firstId", firstId, "count", dto.count()));
    }

    @PostMapping(path = "/event", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Close or open the road nearest to a point.")
    public ResponseEntity<Map<String, Object>> event(@Valid @RequestBody SimEventDto dto) {
        int edge = service.event(dto);
        return ResponseEntity.ok(Map.of("edgeId", edge, "type", dto.type()));
    }

    @PostMapping(path = "/scenario", consumes = "multipart/form-data", produces = "application/json")
    @Operation(summary = "Load and run a YAML scenario.")
    public ResponseEntity<SimScenarioDto> scenario(@RequestPart("file") MultipartFile file) throws IOException {
        SimScenarioDto scen = service.loadScenario(file.getInputStream());
        service.applyScenario(scen);
        return ResponseEntity.ok(scen);
    }

    private Map<String, Object> state() {
        SimulationEngine e = service.engine();
        return Map.of(
                "running",         e.isRunning(),
                "speedMultiplier", e.speedMultiplier(),
                "edgeCount",       e.traffic().edgeCount()
        );
    }
}
