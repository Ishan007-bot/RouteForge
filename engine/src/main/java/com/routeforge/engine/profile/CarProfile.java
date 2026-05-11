package com.routeforge.engine.profile;

import com.routeforge.engine.graph.HighwayClass;
import com.routeforge.engine.graph.RoadGraph;

/**
 * Driving profile. Cost is travel time in seconds based on either the
 * {@code maxspeed} OSM tag (if present) or a per-{@link HighwayClass} default.
 * <p>
 * Forbids genuinely non-drivable surfaces (footways, cycleways, paths, steps,
 * pedestrian zones). Tracks and {@code service} are allowed — service roads
 * are how you reach driveways and parking lots, and tracks are often the
 * only access to rural destinations.
 */
public final class CarProfile implements Profile {

    /** 130 km/h — fastest motorway default in {@link HighwayClass}. */
    private static final double MAX_SPEED_MPS = 130.0 / 3.6;

    @Override
    public String name() { return "car"; }

    @Override
    public double maxSpeedMetersPerSecond() { return MAX_SPEED_MPS; }

    @Override
    public boolean allowed(RoadGraph g, int edgeIndex) {
        return switch (g.highwayClass(edgeIndex)) {
            case FOOTWAY, CYCLEWAY, PATH, STEPS, PEDESTRIAN -> false;
            default -> true;
        };
    }

    @Override
    public double cost(RoadGraph g, int edgeIndex) {
        int kmh = g.maxSpeedKmh(edgeIndex);
        if (kmh == 0) {
            kmh = g.highwayClass(edgeIndex).defaultCarSpeedKmh();
        }
        // length / (km/h * 1000 / 3600)  =  length * 3.6 / km/h  → seconds
        return g.lengthMeters(edgeIndex) * 3.6 / kmh;
    }
}
