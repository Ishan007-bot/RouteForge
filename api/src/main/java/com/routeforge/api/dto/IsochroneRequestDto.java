package com.routeforge.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request the area reachable from a point within a time budget.")
public record IsochroneRequestDto(

        @Schema(description = "Latitude of the start point, in degrees.", example = "47.142")
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
        Double lat,

        @Schema(description = "Longitude of the start point, in degrees.", example = "9.524")
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
        Double lon,

        @Schema(description = "Budget in seconds. 600 = reach in 10 minutes.", example = "600")
        @NotNull @Positive
        Double budgetSeconds,

        @Schema(description = "Travel mode.",
                allowableValues = {"car", "bike", "foot"},
                defaultValue = "car")
        String profile
) { }
