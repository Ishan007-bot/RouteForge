package com.routeforge.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/sim/spawn}.
 *
 * <p>If {@code count} is 1, spawn one vehicle from (fromLat,fromLon) to
 * (toLat,toLon). If {@code count} &gt; 1, spawn that many copies of the
 * same trip — useful for stress-testing routing under load. For randomised
 * fleets, call the endpoint repeatedly with different lat/lons.
 */
public record SimSpawnDto(
        @DecimalMin("-90")  @DecimalMax("90")   double fromLat,
        @DecimalMin("-180") @DecimalMax("180")  double fromLon,
        @DecimalMin("-90")  @DecimalMax("90")   double toLat,
        @DecimalMin("-180") @DecimalMax("180")  double toLon,
        @NotBlank String profile,
        @Min(1)  int count
) { }
