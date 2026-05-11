package com.routeforge.api.controller;

import com.routeforge.api.dto.IsochroneRequestDto;
import com.routeforge.api.dto.IsochroneResponseDto;
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
@Tag(name = "Isochrone", description = "Area reachable within a travel-time budget.")
public class IsochroneController {

    private final RouteService service;

    public IsochroneController(RouteService service) {
        this.service = service;
    }

    @PostMapping(path = "/isochrone", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Compute an isochrone",
            description = "Return every reachable point and its cost from the start, within the budget. "
                        + "Frontend can hull the points into a polygon overlay.")
    public ResponseEntity<IsochroneResponseDto> isochrone(@Valid @RequestBody IsochroneRequestDto req) {
        return ResponseEntity.ok(service.isochrone(req.lat(), req.lon(), req.budgetSeconds(), req.profile()));
    }
}
