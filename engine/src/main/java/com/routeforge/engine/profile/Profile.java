package com.routeforge.engine.profile;

import com.routeforge.engine.graph.RoadGraph;

/**
 * A travel mode (car / bike / foot / ...).
 * <p>
 * Encodes two things about every edge in a graph:
 * <ul>
 *   <li>Is this mode <b>allowed</b> on this edge? (e.g. cars on a footway → no.)</li>
 *   <li>What does it <b>cost</b> to traverse it? (typically duration in seconds.)</li>
 * </ul>
 * The shortest-path algorithms know nothing about roads or cars — they
 * minimize whatever {@link #cost} returns. Swap the profile and you change
 * what "shortest" means without touching the algorithms.
 */
public interface Profile {

    /** Human-readable identifier, e.g. {@code "car"}. */
    String name();

    /**
     * @return {@code true} if this mode is allowed to traverse the given edge.
     */
    boolean allowed(RoadGraph g, int edgeIndex);

    /**
     * Cost of traversing the given edge.
     * Typically duration in seconds: {@code lengthMeters / speedMetersPerSecond}.
     * <p>
     * The value MUST be {@code >= 0}. Returning {@link Double#POSITIVE_INFINITY}
     * is allowed and equivalent to {@link #allowed} returning {@code false}.
     */
    double cost(RoadGraph g, int edgeIndex);

    /**
     * The fastest physically plausible speed for this mode, in <b>meters per second</b>.
     * <p>
     * Used by A* to compute an <i>admissible</i> heuristic: the remaining
     * great-circle distance to the goal, divided by this speed, is always
     * a lower bound on the true remaining duration. If you overestimate
     * this value, the heuristic stops being admissible and A* may return
     * suboptimal paths.
     */
    double maxSpeedMetersPerSecond();
}
