package com.routeforge.engine.profile;

import com.routeforge.engine.graph.RoadGraph;

/**
 * Walking profile. Constant speed (5 km/h). Walkers can use almost any way
 * except motorways and trunks where pedestrians aren't allowed.
 */
public final class FootProfile implements Profile {

    private static final double SPEED_KMH = 5.0;
    private static final double MAX_SPEED_MPS = SPEED_KMH / 3.6;

    @Override
    public String name() { return "foot"; }

    @Override
    public double maxSpeedMetersPerSecond() { return MAX_SPEED_MPS; }

    @Override
    public boolean allowed(RoadGraph g, int edgeIndex) {
        return switch (g.highwayClass(edgeIndex)) {
            case MOTORWAY, TRUNK -> false;
            default -> true;
        };
    }

    @Override
    public double cost(RoadGraph g, int edgeIndex) {
        return g.lengthMeters(edgeIndex) * 3.6 / SPEED_KMH;
    }
}
