package com.routeforge.engine.routing;

import java.util.List;

/**
 * Result of a successful or failed routing query.
 *
 * @param found              {@code true} if a path was found
 * @param nodePath           ordered graph node indices from source to target
 *                           (empty if {@code !found})
 * @param geometry           ordered {@code [lat, lon]} pairs of the path
 * @param distanceMeters     total path length in meters
 * @param durationSeconds    total path duration in seconds (sum of edge costs
 *                           returned by the profile)
 * @param algorithm          name of the algorithm that produced this result
 * @param elapsedMillis      wall-clock time of the search
 * @param nodesSettled       number of nodes pulled off the priority queue
 *                           (a proxy for "work done"; useful for benchmarking)
 */
public record RouteResult(
        boolean found,
        List<Integer> nodePath,
        List<double[]> geometry,
        double distanceMeters,
        double durationSeconds,
        String algorithm,
        long elapsedMillis,
        long nodesSettled
) {

    /** Empty "not-found" result for the given algorithm. */
    public static RouteResult notFound(String algorithm, long elapsedMillis, long nodesSettled) {
        return new RouteResult(false, List.of(), List.of(), 0.0, 0.0,
                algorithm, elapsedMillis, nodesSettled);
    }
}
