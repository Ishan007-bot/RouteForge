package com.routeforge.api.controller;

import com.routeforge.api.dto.RouteRequestDto;
import com.routeforge.api.dto.RouteResponseDto;
import com.routeforge.api.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Routing", description = "Plan routes between two points.")
public class RouteController {

    private final RouteService service;

    public RouteController(RouteService service) {
        this.service = service;
    }

    @PostMapping(path = "/route", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Plan a route",
            description = "Given two coordinates and a profile, return the optimal path's geometry, "
                        + "distance, duration, and search metadata. Results are cached.")
    public ResponseEntity<RouteResponseDto> route(@Valid @RequestBody RouteRequestDto req) {
        return ResponseEntity.ok(service.route(req));
    }
}
