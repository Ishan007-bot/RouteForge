package com.routeforge.engine.algo;

import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.profile.Profile;
import com.routeforge.engine.routing.RouteResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Classic Dijkstra single-source single-target shortest path with an
 * indexed binary heap (so decrease-key is O(log n) instead of O(n)).
 *
 * <h2>Algorithm in one paragraph</h2>
 * Maintain a "distance so far" estimate {@code dist[v]} for every node,
 * starting at {@code +infinity} except {@code dist[source] = 0}. Keep a
 * priority queue of nodes ordered by their current {@code dist}. Repeatedly
 * pop the cheapest unprocessed node {@code u} ("settle" it — its distance
 * is now final) and "relax" each outgoing edge {@code (u, v)}: if going
 * through {@code u} gives a shorter {@code dist[v]}, update it and either
 * insert or decrease-key {@code v} in the queue.
 * <p>
 * Stops as soon as the target is popped (its distance is then optimal).
 *
 * <h2>Complexity</h2>
 * O((n + m) log n) with the indexed binary heap, where n = nodes, m = edges.
 */
public final class Dijkstra implements ShortestPath {

    @Override public String name() { return "dijkstra"; }

    @Override
    public RouteResult shortestPath(RoadGraph g, int source, int target, Profile profile) {
        int n = g.nodeCount();
        if (source < 0 || source >= n) throw new IndexOutOfBoundsException("source: " + source);
        if (target < 0 || target >= n) throw new IndexOutOfBoundsException("target: " + target);

        long startNs = System.nanoTime();

        // dist[v] = best known cost from source to v
        // prevEdge[v] = the edge we entered v through on the best known path
        //                (-1 if v not reached yet)
        double[] dist = new double[n];
        int[] prevEdge = new int[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(prevEdge, -1);
        dist[source] = 0.0;

        IndexedBinaryHeap heap = new IndexedBinaryHeap(n);
        heap.insertOrDecrease(source, 0.0);

        long settled = 0;

        while (!heap.isEmpty()) {
            int u = heap.pollMin();
            settled++;

            // Early termination: as soon as we settle the target, its distance is optimal.
            if (u == target) {
                return buildResult(g, source, target, dist, prevEdge,
                        true, startNs, settled);
            }

            // Relax each outgoing edge.
            int end = g.endEdge(u);
            for (int e = g.firstEdge(u); e < end; e++) {
                if (!profile.allowed(g, e)) continue;
                int v = g.target(e);
                double newDist = dist[u] + profile.cost(g, e);
                if (newDist < dist[v]) {
                    dist[v] = newDist;
                    prevEdge[v] = e;
                    heap.insertOrDecrease(v, newDist);
                }
            }
        }

        // Heap drained without reaching target → unreachable.
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        return RouteResult.notFound(name(), elapsedMs, settled);
    }

    /**
     * Walk {@code prevEdge[]} backward from target to source, reverse, then
     * sum lengths and construct geometry. Shared with A* below — extracted
     * here as a package-private helper so we don't duplicate it.
     */
    static RouteResult buildResult(RoadGraph g, int source, int target,
                                   double[] dist, int[] prevEdge,
                                   boolean found, long startNs, long settled) {
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        if (!found) return RouteResult.notFound("dijkstra", elapsedMs, settled);

        // Walk back from target to source via prevEdge[].
        // We need a way to find the source of an edge — CSR doesn't give us that
        // directly, but the predecessor node is "the node whose edge list contains
        // prevEdge[v]". We reconstruct by walking node-by-node from target.
        //
        // Easier: store predecessor node alongside prevEdge. We add a small pass
        // here to derive it, but a cleaner version would track prevNode[] explicitly.
        // For correctness and simplicity, do that explicit walk now:
        ArrayList<Integer> nodesReversed = new ArrayList<>();
        ArrayList<Integer> edgesReversed = new ArrayList<>();
        int cur = target;
        nodesReversed.add(cur);
        while (cur != source) {
            int incoming = prevEdge[cur];
            // The predecessor is the node whose out-edges include 'incoming'.
            // Binary search edgeOffsets for the right bucket.
            int pred = predecessorOf(g, incoming);
            edgesReversed.add(incoming);
            nodesReversed.add(pred);
            cur = pred;
        }
        Collections.reverse(nodesReversed);
        Collections.reverse(edgesReversed);

        // Geometry: one point per node visited.
        List<double[]> geom = new ArrayList<>(nodesReversed.size());
        double meters = 0.0;
        for (int v : nodesReversed) {
            geom.add(new double[]{ g.lat(v), g.lon(v) });
        }
        for (int e : edgesReversed) {
            meters += g.lengthMeters(e);
        }

        // duration = settled distance value at target (profile-defined units,
        // typically seconds).
        double duration = dist[target];

        return new RouteResult(true, List.copyOf(nodesReversed), geom,
                meters, duration, "dijkstra", elapsedMs, settled);
    }

    /**
     * Find the source node of an edge given its global index.
     * Delegates to {@link RoadGraph#source(int)}; kept here so existing
     * package-private callers (A*, Bidirectional) don't need to be edited.
     */
    static int predecessorOf(RoadGraph g, int edgeIndex) {
        return g.source(edgeIndex);
    }
}
