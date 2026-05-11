package com.routeforge.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Set of reachable points within the requested budget.")
public record IsochroneResponseDto(

        @Schema(description = "Profile used.")
        String profile,

        @Schema(description = "Time budget in seconds.")
        double budgetSeconds,

        @Schema(description = "Wall-clock time of the search.")
        long elapsedMillis,

        @Schema(description = "Number of reachable nodes.")
        int nodeCount,

        @Schema(description = "Reachable points, each as [lat, lon, secondsFromOrigin].")
        List<double[]> points
) { }
