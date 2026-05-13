package com.routeforge.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * Body of {@code POST /api/sim/control}: lifecycle commands for the sim.
 *
 * <p>Any combination of fields may be present. {@code null} means "leave
 * unchanged". This makes it convenient for the frontend to send partial
 * updates like {@code { "speedMultiplier": 2.0 }}.
 *
 * <p>{@code action}:
 * <ul>
 *   <li>{@code "play"}  — start ticking</li>
 *   <li>{@code "pause"} — stop ticking</li>
 *   <li>{@code "reset"} — pause + clear all vehicles + reopen all edges</li>
 *   <li>{@code null}    — no lifecycle change (only speed update)</li>
 * </ul>
 */
public record SimControlDto(
        String action,
        @DecimalMin("0.1") @DecimalMax("8.0") Double speedMultiplier
) { }
