package com.routeforge.engine.sim;

import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.profile.Profile;

/**
 * Decorator: a {@link Profile} whose cost is the base cost multiplied by
 * the live BPR congestion factor from a {@link TrafficModel}, with closed
 * edges reported as disallowed.
 *
 * <p>{@link #maxSpeedMetersPerSecond()} is taken from the base profile.
 * That keeps the A* heuristic admissible: it underestimates duration on
 * congested edges, which is the safe direction (overestimating would
 * make A* return suboptimal paths).
 */
public final class TrafficAwareProfile implements Profile {

    private final Profile base;
    private final TrafficModel traffic;

    public TrafficAwareProfile(Profile base, TrafficModel traffic) {
        this.base    = base;
        this.traffic = traffic;
    }

    @Override public String name() { return base.name() + "+traffic"; }

    @Override
    public boolean allowed(RoadGraph g, int edgeIndex) {
        if (traffic.isClosed(edgeIndex)) return false;
        return base.allowed(g, edgeIndex);
    }

    @Override
    public double cost(RoadGraph g, int edgeIndex) {
        if (traffic.isClosed(edgeIndex)) return Double.POSITIVE_INFINITY;
        return base.cost(g, edgeIndex) * traffic.congestionFactor(edgeIndex);
    }

    @Override
    public double maxSpeedMetersPerSecond() {
        return base.maxSpeedMetersPerSecond();
    }
}
