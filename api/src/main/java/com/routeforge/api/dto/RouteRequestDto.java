package com.routeforge.api.dto;

import com.routeforge.engine.routing.RouteRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Public HTTP contract for {@code POST /api/route}.
 * <p>
 * We use a DTO instead of accepting {@link RouteRequest} directly so the API
 * contract (field names, validation, defaults) can evolve independently of
 * the engine's internal types.
 */
@Schema(description = "A request to plan a route between two coordinates.")
public record RouteRequestDto(

        @Schema(description = "Latitude of the start point, in degrees.", example = "47.142")
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
        Double fromLat,

        @Schema(description = "Longitude of the start point, in degrees.", example = "9.524")
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
        Double fromLon,

        @Schema(description = "Latitude of the destination, in degrees.", example = "47.166")
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
        Double toLat,

        @Schema(description = "Longitude of the destination, in degrees.", example = "9.510")
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
        Double toLon,

        @Schema(description = "Travel mode.",
                allowableValues = {"car", "bike", "foot"},
                defaultValue = "car")
        String profile,

        @Schema(description = "Algorithm to run.",
                allowableValues = {"dijkstra", "astar", "bidirectional", "ch"},
                defaultValue = "astar")
        String algo
) {

    public RouteRequest toEngineRequest() {
        return new RouteRequest(
                fromLat, fromLon, toLat, toLon,
                profile == null || profile.isBlank() ? "car"   : profile,
                algo    == null || algo.isBlank()    ? "astar" : algo
        );
    }
}
