package com.routeforge.api.dto;

import com.routeforge.engine.routing.RouteResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response shape for {@code POST /api/route}.
 * <p>
 * Geometry is a list of {@code [lat, lon]} pairs (NOT GeoJSON, which uses
 * {@code [lon, lat]}). The frontend converts before passing to MapLibre.
 * We chose lat-lon here to match how humans think about coordinates.
 */
@Schema(description = "A computed route or an explanation of why none was found.")
public record RouteResponseDto(

        @Schema(description = "True if a path was found.")
        boolean found,

        @Schema(description = "Algorithm that produced this result.")
        String algorithm,

        @Schema(description = "Profile used.")
        String profile,

        @Schema(description = "Total length of the path in meters.")
        double distanceMeters,

        @Schema(description = "Total cost in the profile's units (seconds for time-based profiles).")
        double durationSeconds,

        @Schema(description = "Wall-clock time of the search itself, in milliseconds.")
        long elapsedMillis,

        @Schema(description = "Number of nodes pulled off the priority queue.")
        long nodesSettled,

        @Schema(description = "Ordered [lat, lon] points along the path.")
        List<double[]> geometry
) {

    public static RouteResponseDto from(RouteResult r, String profileName) {
        return new RouteResponseDto(
                r.found(),
                r.algorithm(),
                profileName,
                r.distanceMeters(),
                r.durationSeconds(),
                r.elapsedMillis(),
                r.nodesSettled(),
                r.geometry()
        );
    }
}
