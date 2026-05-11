package com.routeforge.api.controller;

import com.routeforge.api.service.RouteService;
import com.routeforge.engine.graph.RoadGraph;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Info", description = "Service metadata and capabilities.")
public class InfoController {

    private final RouteService service;

    public InfoController(RouteService service) {
        this.service = service;
    }

    @GetMapping(path = "/info", produces = "application/json")
    @Operation(summary = "Engine and service metadata")
    public Map<String, Object> info() {
        RoadGraph g = service.graph();
        return Map.of(
                "service", "routeforge-api",
                "version", "0.1.0-SNAPSHOT",
                "graph", Map.of(
                        "nodes", g.nodeCount(),
                        "edges", g.edgeCount()
                ),
                "profiles",   List.of("car", "bike", "foot"),
                "algorithms", List.of("dijkstra", "astar", "bidirectional", "ch")
        );
    }
}
