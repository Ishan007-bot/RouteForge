package com.routeforge.engine.algo;

import com.routeforge.engine.geom.Haversine;
import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.profile.Profile;
import com.routeforge.engine.routing.RouteResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A* search: Dijkstra plus a heuristic that biases exploration toward the goal.
 *
 * <h2>The one-paragraph difference from Dijkstra</h2>
 * Dijkstra prioritizes nodes by {@code g(v)} = best known cost from source.
 * A* prioritizes by {@code f(v) = g(v) + h(v)}, where {@code h(v)} is a
 * lower bound on the remaining cost from {@code v} to the target. As long
 * as {@code h} never overestimates ("admissible") and the cost function is
 * non-negative, A* still finds the optimal path — but explores far fewer
 * nodes, because it stops chasing branches whose {@code f} is already worse
 * than a known route.
 *
 * <h2>The heuristic we use</h2>
 * {@code h(v) = haversine_distance(v, target) / profile.maxSpeed}.
 * Straight-line distance / max plausible speed = a lower bound on the true
 * remaining duration. Admissible by construction.
 */
public final class AStar implements ShortestPath {

    @Override public String name() { return "astar"; }

    @Override
    public RouteResult shortestPath(RoadGraph g, int source, int target, Profile profile) {
        int n = g.nodeCount();
        if (source < 0 || source >= n) throw new IndexOutOfBoundsException("source: " + source);
        if (target < 0 || target >= n) throw new IndexOutOfBoundsException("target: " + target);

        long startNs = System.nanoTime();
        double targetLat = g.lat(target);
        double targetLon = g.lon(target);
        double invMaxSpeed = 1.0 / profile.maxSpeedMetersPerSecond();

        double[] gScore = new double[n];   // best known cost from source
        int[]    prevEdge = new int[n];
        Arrays.fill(gScore, Double.POSITIVE_INFINITY);
        Arrays.fill(prevEdge, -1);
        gScore[source] = 0.0;

        IndexedBinaryHeap open = new IndexedBinaryHeap(n);
        open.insertOrDecrease(source, heuristic(g, source, targetLat, targetLon, invMaxSpeed));

        long settled = 0;

        while (!open.isEmpty()) {
            int u = open.pollMin();
            settled++;

            if (u == target) {
                return buildAStarResult(g, source, target, gScore, prevEdge, startNs, settled);
            }

            int end = g.endEdge(u);
            for (int e = g.firstEdge(u); e < end; e++) {
                if (!profile.allowed(g, e)) continue;
                int v = g.target(e);
                double tentativeG = gScore[u] + profile.cost(g, e);
                if (tentativeG < gScore[v]) {
                    gScore[v] = tentativeG;
                    prevEdge[v] = e;
                    double f = tentativeG + heuristic(g, v, targetLat, targetLon, invMaxSpeed);
                    open.insertOrDecrease(v, f);
                }
            }
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        return RouteResult.notFound(name(), elapsedMs, settled);
    }

    /** Straight-line lower bound on remaining cost (seconds). */
    private static double heuristic(RoadGraph g, int node,
                                    double targetLat, double targetLon, double invMaxSpeed) {
        return Haversine.distanceMeters(g.lat(node), g.lon(node), targetLat, targetLon) * invMaxSpeed;
    }

    /** Path reconstruction. Same shape as Dijkstra but tags the result with "astar". */
    private static RouteResult buildAStarResult(RoadGraph g, int source, int target,
                                                double[] gScore, int[] prevEdge,
                                                long startNs, long settled) {
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        ArrayList<Integer> nodesReversed = new ArrayList<>();
        ArrayList<Integer> edgesReversed = new ArrayList<>();
        int cur = target;
        nodesReversed.add(cur);
        while (cur != source) {
            int incoming = prevEdge[cur];
            int pred = Dijkstra.predecessorOf(g, incoming);
            edgesReversed.add(incoming);
            nodesReversed.add(pred);
            cur = pred;
        }
        Collections.reverse(nodesReversed);
        Collections.reverse(edgesReversed);

        List<double[]> geom = new ArrayList<>(nodesReversed.size());
        double meters = 0.0;
        for (int v : nodesReversed) geom.add(new double[]{ g.lat(v), g.lon(v) });
        for (int e : edgesReversed) meters += g.lengthMeters(e);

        return new RouteResult(true, List.copyOf(nodesReversed), geom,
                meters, gScore[target], "astar", elapsedMs, settled);
    }
}
