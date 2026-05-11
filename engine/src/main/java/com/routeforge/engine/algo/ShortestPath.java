package com.routeforge.engine.algo;

import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.profile.Profile;
import com.routeforge.engine.routing.RouteResult;

/**
 * A shortest-path algorithm on a {@link RoadGraph}.
 * <p>
 * All implementations share the same contract: given a graph, source and
 * target node indices, and a {@link Profile} (which decides edge cost and
 * traversability), return a {@link RouteResult}. This uniform interface
 * is what lets the API expose an {@code ?algo=} parameter without each
 * algorithm needing a custom wrapper.
 */
public interface ShortestPath {

    /** Short name used in results and CLI ({@code "dijkstra"}, {@code "astar"}, ...). */
    String name();

    /**
     * Find a shortest path from {@code source} to {@code target} under
     * the given profile.
     *
     * @param graph    road graph
     * @param source   node index of the start
     * @param target   node index of the destination
     * @param profile  cost / access function
     * @return a {@link RouteResult}; check {@link RouteResult#found()} for success
     * @throws IndexOutOfBoundsException if source/target are not valid node indices
     */
    RouteResult shortestPath(RoadGraph graph, int source, int target, Profile profile);
}
