package com.routeforge.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code POST /api/sim/event}: a road-network change.
 *
 * <p>{@code type} is one of:
 * <ul>
 *   <li>{@code "close"} — close the edge nearest to (lat,lon)</li>
 *   <li>{@code "open"}  — re-open the edge nearest to (lat,lon)</li>
 * </ul>
 *
 * <p>If {@code edgeId} is set, it overrides the lat/lon lookup and acts
 * directly on that edge — used by the scenario loader to reproduce
 * specific event sequences deterministically.
 */
public record SimEventDto(
        @NotBlank @Pattern(regexp = "close|open") String type,
        @DecimalMin("-90")  @DecimalMax("90")  double lat,
        @DecimalMin("-180") @DecimalMax("180") double lon,
        Integer edgeId
) { }
