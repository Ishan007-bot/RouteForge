package com.routeforge.engine.algo;

import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.profile.Profile;
import com.routeforge.engine.routing.RouteResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Bidirectional Dijkstra: two simultaneous searches that meet in the middle.
 *
 * <h2>Intuition</h2>
 * One search expands forward from the source; another expands backward from
 * the target (using the reverse adjacency). Each search alone is a Dijkstra.
 * Combined, they explore a region shaped like two overlapping ellipses with
 * a smaller total area than a single Dijkstra would cover from source — so
 * fewer nodes get settled and the query is faster (typically by ~2x on
 * sparse road networks, more on denser ones).
 *
 * <h2>Termination — the subtle part</h2>
 * Let {@code mu} be the best total cost we've found so far via any node
 * reached by <i>both</i> searches: {@code mu = min(distF[v] + distB[v])}.
 * We can stop as soon as {@code topF + topB >= mu} — at that point no
 * combination of unsettled nodes from each side can beat {@code mu}, since
 * any further improvement would need a meeting node with
 * {@code distF >= topF} and {@code distB >= topB}.
 *
 * <h2>Path reconstruction</h2>
 * Build the forward path from source to the meeting node by walking
 * {@code prevEdgeF[]}, then append the backward path from the meeting node
 * to target by walking {@code prevInEdgeB[]} in reverse.
 */
public final class BidirectionalDijkstra implements ShortestPath {

    @Override public String name() { return "bidirectional"; }

    @Override
    public RouteResult shortestPath(RoadGraph g, int source, int target, Profile profile) {
        int n = g.nodeCount();
        if (source < 0 || source >= n) throw new IndexOutOfBoundsException("source: " + source);
        if (target < 0 || target >= n) throw new IndexOutOfBoundsException("target: " + target);

        long startNs = System.nanoTime();

        // Edge case: source == target.
        if (source == target) {
            long ms = (System.nanoTime() - startNs) / 1_000_000;
            return new RouteResult(true, List.of(source),
                    List.of(new double[]{ g.lat(source), g.lon(source) }),
                    0.0, 0.0, name(), ms, 0);
        }

        double[] distF = new double[n];
        double[] distB = new double[n];
        int[] prevEdgeF = new int[n];     // forward: the forward edge we entered v through
        int[] prevInEdgeB = new int[n];   // backward: the in-edge index we used to back-reach v
        boolean[] settledF = new boolean[n];
        boolean[] settledB = new boolean[n];
        Arrays.fill(distF, Double.POSITIVE_INFINITY);
        Arrays.fill(distB, Double.POSITIVE_INFINITY);
        Arrays.fill(prevEdgeF, -1);
        Arrays.fill(prevInEdgeB, -1);
        distF[source] = 0;
        distB[target] = 0;

        IndexedBinaryHeap pqF = new IndexedBinaryHeap(n);
        IndexedBinaryHeap pqB = new IndexedBinaryHeap(n);
        pqF.insertOrDecrease(source, 0);
        pqB.insertOrDecrease(target, 0);

        double mu = Double.POSITIVE_INFINITY;
        int meetingNode = -1;
        long settled = 0;

        while (!pqF.isEmpty() && !pqB.isEmpty()) {
            double topF = distF[pqF.peekMin()];
            double topB = distB[pqB.peekMin()];
            if (topF + topB >= mu) break;

            // -------- Forward step --------
            int u = pqF.pollMin();
            settled++;
            settledF[u] = true;
            // If the backward search has reached u, we have a candidate meet.
            if (distB[u] != Double.POSITIVE_INFINITY) {
                double total = distF[u] + distB[u];
                if (total < mu) { mu = total; meetingNode = u; }
            }
            int endF = g.endEdge(u);
            for (int e = g.firstEdge(u); e < endF; e++) {
                if (!profile.allowed(g, e)) continue;
                int v = g.target(e);
                if (settledF[v]) continue;
                double nd = distF[u] + profile.cost(g, e);
                if (nd < distF[v]) {
                    distF[v] = nd;
                    prevEdgeF[v] = e;
                    pqF.insertOrDecrease(v, nd);
                    if (distB[v] != Double.POSITIVE_INFINITY) {
                        double total = nd + distB[v];
                        if (total < mu) { mu = total; meetingNode = v; }
                    }
                }
            }

            if (pqB.isEmpty()) break;
            // -------- Backward step (over incoming edges) --------
            int x = pqB.pollMin();
            settled++;
            settledB[x] = true;
            if (distF[x] != Double.POSITIVE_INFINITY) {
                double total = distF[x] + distB[x];
                if (total < mu) { mu = total; meetingNode = x; }
            }
            int endB = g.endInEdge(x);
            for (int i = g.firstInEdge(x); i < endB; i++) {
                int forwardEdge = g.inEdgeForwardIndex(i);
                if (!profile.allowed(g, forwardEdge)) continue;
                int p = g.inEdgeSource(i);
                if (settledB[p]) continue;
                double nd = distB[x] + profile.cost(g, forwardEdge);
                if (nd < distB[p]) {
                    distB[p] = nd;
                    prevInEdgeB[p] = i;
                    pqB.insertOrDecrease(p, nd);
                    if (distF[p] != Double.POSITIVE_INFINITY) {
                        double total = distF[p] + nd;
                        if (total < mu) { mu = total; meetingNode = p; }
                    }
                }
            }
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        if (meetingNode == -1) {
            return RouteResult.notFound(name(), elapsedMs, settled);
        }

        return reconstruct(g, source, target, meetingNode,
                distF, distB, prevEdgeF, prevInEdgeB, elapsedMs, settled);
    }

    /**
     * Stitch the forward half (source → meeting) and reversed backward half
     * (meeting → target) into a single route.
     */
    private RouteResult reconstruct(RoadGraph g, int source, int target, int meeting,
                                    double[] distF, double[] distB,
                                    int[] prevEdgeF, int[] prevInEdgeB,
                                    long elapsedMs, long settled) {
        // Forward half: walk prevEdgeF[] from meeting back to source.
        ArrayList<Integer> fwdNodesReversed = new ArrayList<>();
        ArrayList<Integer> fwdEdgesReversed = new ArrayList<>();
        int cur = meeting;
        fwdNodesReversed.add(cur);
        while (cur != source) {
            int incoming = prevEdgeF[cur];
            int pred = Dijkstra.predecessorOf(g, incoming);
            fwdEdgesReversed.add(incoming);
            fwdNodesReversed.add(pred);
            cur = pred;
        }
        Collections.reverse(fwdNodesReversed);
        Collections.reverse(fwdEdgesReversed);

        // Backward half: walk prevInEdgeB[] from meeting forward to target.
        // prevInEdgeB[v] is the in-edge we used during backward search to reach v
        //   from some node w. Forward direction: edge from v to w.
        ArrayList<Integer> bwdNodes = new ArrayList<>();
        ArrayList<Integer> bwdEdges = new ArrayList<>();
        cur = meeting;
        while (cur != target) {
            int inEdge = prevInEdgeB[cur];
            int forwardEdge = g.inEdgeForwardIndex(inEdge);
            int next = g.target(forwardEdge); // the "w" that the in-edge came from
            bwdEdges.add(forwardEdge);
            bwdNodes.add(next);
            cur = next;
        }

        // Combine: fwdNodes + bwdNodes (meeting node already in fwdNodes).
        ArrayList<Integer> allNodes = new ArrayList<>(fwdNodesReversed.size() + bwdNodes.size());
        allNodes.addAll(fwdNodesReversed);
        allNodes.addAll(bwdNodes);

        ArrayList<Integer> allEdges = new ArrayList<>(fwdEdgesReversed.size() + bwdEdges.size());
        allEdges.addAll(fwdEdgesReversed);
        allEdges.addAll(bwdEdges);

        List<double[]> geom = new ArrayList<>(allNodes.size());
        double meters = 0.0;
        double seconds = distF[meeting] + distB[meeting];
        for (int v : allNodes) geom.add(new double[]{ g.lat(v), g.lon(v) });
        for (int e : allEdges) meters += g.lengthMeters(e);

        return new RouteResult(true, List.copyOf(allNodes), geom,
                meters, seconds, name(), elapsedMs, settled);
    }
}
