package com.routeforge.engine.algo;

import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.profile.Profile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Single-source many-target reachability: from a starting node, find every
 * node whose shortest-path cost is below a budget.
 * <p>
 * The classic use case: <i>"show me everywhere I can drive in 15 minutes."</i>
 * Frontend hulls the returned points into a polygon ("isochrone" = lines of
 * equal travel time, like contour lines on a map).
 *
 * <h2>Algorithm</h2>
 * Dijkstra without a target. Settle nodes in order of distance from the
 * source; when the next node's distance would exceed {@code maxCostSeconds},
 * stop. Everything settled so far is in the reachable set.
 *
 * <h2>Complexity</h2>
 * O((n + m) log n) like Dijkstra, but typically touches a tiny fraction
 * of the graph because {@code maxCostSeconds} bounds the explored region.
 */
public final class Isochrone {

    /**
     * @param graph           road graph
     * @param sourceNode      starting node index
     * @param maxCostSeconds  budget (in the profile's cost units, typically seconds)
     * @param profile         cost / access function
     * @return ordered list of reachable nodes with their cost from {@code sourceNode}
     */
    public Result compute(RoadGraph graph, int sourceNode, double maxCostSeconds, Profile profile) {
        if (sourceNode < 0 || sourceNode >= graph.nodeCount()) {
            throw new IndexOutOfBoundsException("sourceNode: " + sourceNode);
        }
        if (maxCostSeconds < 0) throw new IllegalArgumentException("maxCostSeconds < 0");

        long startNs = System.nanoTime();
        int n = graph.nodeCount();
        double[] dist = new double[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[sourceNode] = 0.0;

        IndexedBinaryHeap heap = new IndexedBinaryHeap(n);
        heap.insertOrDecrease(sourceNode, 0.0);

        List<ReachedNode> reached = new ArrayList<>();
        long settled = 0;

        while (!heap.isEmpty()) {
            int u = heap.peekMin();
            if (dist[u] > maxCostSeconds) break;   // first node outside budget → done
            heap.pollMin();
            settled++;
            reached.add(new ReachedNode(u, dist[u]));

            int end = graph.endEdge(u);
            for (int e = graph.firstEdge(u); e < end; e++) {
                if (!profile.allowed(graph, e)) continue;
                int v = graph.target(e);
                double nd = dist[u] + profile.cost(graph, e);
                if (nd < dist[v]) {
                    dist[v] = nd;
                    heap.insertOrDecrease(v, nd);
                }
            }
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        return new Result(reached, elapsedMs, settled);
    }

    /** One reachable node with its travel cost from the isochrone source. */
    public record ReachedNode(int nodeIndex, double costSeconds) { }

    /**
     * @param nodes         all nodes reachable within budget, in settle order
     * @param elapsedMillis wall-clock time of the search
     * @param nodesSettled  count of nodes pulled from the priority queue
     */
    public record Result(List<ReachedNode> nodes, long elapsedMillis, long nodesSettled) { }
}
