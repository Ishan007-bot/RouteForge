package com.routeforge.engine.profile;

import com.routeforge.engine.graph.RoadGraph;

/**
 * Cycling profile. Constant speed (15 km/h) — Phase 1 doesn't yet model
 * elevation, surface, or cycle-friendliness penalties. We'll add those later.
 * <p>
 * Forbids motorways/trunks (illegal/dangerous for bikes) and steps. Allows
 * everything else including footways (cyclists often share them).
 */
public final class BikeProfile implements Profile {

    private static final double SPEED_KMH = 15.0;
    private static final double MAX_SPEED_MPS = SPEED_KMH / 3.6;

    @Override
    public String name() { return "bike"; }

    @Override
    public double maxSpeedMetersPerSecond() { return MAX_SPEED_MPS; }

    @Override
    public boolean allowed(RoadGraph g, int edgeIndex) {
        return switch (g.highwayClass(edgeIndex)) {
            case MOTORWAY, TRUNK, STEPS -> false;
            default -> true;
        };
    }

    @Override
    public double cost(RoadGraph g, int edgeIndex) {
        return g.lengthMeters(edgeIndex) * 3.6 / SPEED_KMH;
    }
}
