package com.routeforge.engine.ch;

import com.routeforge.engine.algo.IndexedBinaryHeap;
import com.routeforge.engine.algo.ShortestPath;
import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.profile.Profile;
import com.routeforge.engine.routing.RouteResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Bidirectional "upward" query on a {@link CHGraph}.
 *
 * <h2>The query in one paragraph</h2>
 * Run two Dijkstras: a forward search from {@code source} that only relaxes
 * edges going to <i>higher-level</i> nodes (using {@code upFwd}), and a
 * backward search from {@code target} that only relaxes incoming edges
 * <i>from</i> higher-level nodes (using {@code upBwd}). Each search alone
 * walks a small "upward subgraph" of the hierarchy. They meet at the highest
 * common ancestor (the unique node where the two ellipses kiss); from there
 * we know the optimal cost and can stitch a path.
 *
 * <h2>Path unpacking</h2>
 * The path we reconstruct uses unified edge IDs, some of which are shortcuts.
 * For each shortcut we recursively expand it into its two underlying edges
 * via {@link CHGraph#shortcutLowerA} / {@code shortcutLowerB}. The result is
 * a sequence of <i>original</i> graph edges that the result geometry is
 * built from.
 *
 * <h2>Why this is fast</h2>
 * Each search only sees an "up-tree" of the graph; that tree is shallow
 * and thin (typically log-size) compared to the full road network, so even
 * continental-scale queries settle on the order of hundreds (not millions)
 * of nodes. Sub-millisecond is normal on a Germany-sized graph.
 */
public final class CHQuery implements ShortestPath {

    private final CHGraph ch;

    public CHQuery(CHGraph ch) {
        this.ch = ch;
    }

    public CHGraph chGraph() { return ch; }

    @Override public String name() { return "ch"; }

    @Override
    public RouteResult shortestPath(RoadGraph graph, int source, int target, Profile profile) {
        // The graph parameter must match the one the CH was built from. We trust the caller.
        if (graph != ch.base()) {
            throw new IllegalArgumentException("CHQuery: graph mismatch with preprocessed CHGraph");
        }
        long startNs = System.nanoTime();

        int n = ch.nodeCount();
        if (source < 0 || source >= n) throw new IndexOutOfBoundsException("source: " + source);
        if (target < 0 || target >= n) throw new IndexOutOfBoundsException("target: " + target);

        if (source == target) {
            long ms = (System.nanoTime() - startNs) / 1_000_000;
            return new RouteResult(true, List.of(source),
                    List.of(new double[]{ graph.lat(source), graph.lon(source) }),
                    0.0, 0.0, name(), ms, 0);
        }

        // Per-node forward/backward distances, predecessor edge IDs, settled markers.
        double[] distF = new double[n];
        double[] distB = new double[n];
        int[] prevEdgeF = new int[n];   // unified edge ID via which we entered (forward)
        int[] prevEdgeB = new int[n];   // unified edge ID via which we entered (backward)
        Arrays.fill(distF, Double.POSITIVE_INFINITY);
        Arrays.fill(distB, Double.POSITIVE_INFINITY);
        Arrays.fill(prevEdgeF, -1);
        Arrays.fill(prevEdgeB, -1);
        distF[source] = 0;
        distB[target] = 0;

        IndexedBinaryHeap pqF = new IndexedBinaryHeap(n);
        IndexedBinaryHeap pqB = new IndexedBinaryHeap(n);
        pqF.insertOrDecrease(source, 0);
        pqB.insertOrDecrease(target, 0);

        double mu = Double.POSITIVE_INFINITY;
        int meeting = -1;
        long settled = 0;

        while (!pqF.isEmpty() || !pqB.isEmpty()) {
            // Stop when no further candidate can beat mu.
            double topF = pqF.isEmpty() ? Double.POSITIVE_INFINITY : distF[pqF.peekMin()];
            double topB = pqB.isEmpty() ? Double.POSITIVE_INFINITY : distB[pqB.peekMin()];
            if (Math.min(topF, topB) >= mu) break;

            // Forward step (only if its top is still useful).
            if (!pqF.isEmpty() && topF < mu) {
                int u = pqF.pollMin();
                settled++;
                if (distB[u] < Double.POSITIVE_INFINITY) {
                    double total = distF[u] + distB[u];
                    if (total < mu) { mu = total; meeting = u; }
                }
                int end = ch.endUpFwd(u);
                for (int slot = ch.firstUpFwd(u); slot < end; slot++) {
                    int eid = ch.upFwdEdgeId(slot);
                    int v = ch.edgeTarget(eid);
                    double nd = distF[u] + ch.edgeCost(eid, profile);
                    if (nd < distF[v]) {
                        distF[v] = nd;
                        prevEdgeF[v] = eid;
                        pqF.insertOrDecrease(v, nd);
                        if (distB[v] < Double.POSITIVE_INFINITY) {
                            double total = nd + distB[v];
                            if (total < mu) { mu = total; meeting = v; }
                        }
                    }
                }
            }

            // Backward step (only if its top is still useful).
            if (!pqB.isEmpty() && topB < mu) {
                int v = pqB.pollMin();
                settled++;
                if (distF[v] < Double.POSITIVE_INFINITY) {
                    double total = distF[v] + distB[v];
                    if (total < mu) { mu = total; meeting = v; }
                }
                int end = ch.endUpBwd(v);
                for (int slot = ch.firstUpBwd(v); slot < end; slot++) {
                    int eid = ch.upBwdEdgeId(slot);
                    // This edge goes from some u (high-level) to v; backward search
                    // crosses it from v back to u.
                    int u = ch.edgeSource(eid);
                    double nd = distB[v] + ch.edgeCost(eid, profile);
                    if (nd < distB[u]) {
                        distB[u] = nd;
                        prevEdgeB[u] = eid;
                        pqB.insertOrDecrease(u, nd);
                        if (distF[u] < Double.POSITIVE_INFINITY) {
                            double total = distF[u] + nd;
                            if (total < mu) { mu = total; meeting = u; }
                        }
                    }
                }
            }
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        if (meeting == -1) return RouteResult.notFound(name(), elapsedMs, settled);

        return buildResult(graph, source, target, meeting,
                distF, distB, prevEdgeF, prevEdgeB, elapsedMs, settled);
    }

    /** Stitch the two halves and unpack shortcuts into original edges. */
    private RouteResult buildResult(RoadGraph g, int source, int target, int meeting,
                                    double[] distF, double[] distB,
                                    int[] prevEdgeF, int[] prevEdgeB,
                                    long elapsedMs, long settled) {
        // Walk forward half (meeting → source) and unpack each edge.
        List<Integer> baseEdgesFwdReversed = new ArrayList<>();
        int cur = meeting;
        while (cur != source) {
            int eid = prevEdgeF[cur];
            unpackInto(eid, baseEdgesFwdReversed, /* reverse */ true);
            cur = ch.edgeSource(eid);
        }
        Collections.reverse(baseEdgesFwdReversed);

        // Walk backward half (meeting → target via reverse prev pointers).
        // prevEdgeB[u] is the unified edge ID (originally pointing toward u from
        // some w with level[w] > level[u]). For the backward search at v, we used
        // it to reach u; so the natural forward direction crosses it from u to v=ch.edgeTarget(eid)? No:
        // the edge has source=u and target=w (where u has lower level than w).
        // The forward direction of THE PATH is meeting -> ... -> target, which crosses
        // these edges forward (u to w). So to walk meeting -> target we follow the
        // edges as-is, but the loop walks them in the opposite of "search order".
        List<Integer> baseEdgesBwd = new ArrayList<>();
        cur = meeting;
        while (cur != target) {
            int eid = prevEdgeB[cur];
            unpackInto(eid, baseEdgesBwd, /* reverse */ false);
            cur = ch.edgeTarget(eid);
        }

        // Combine.
        List<Integer> allBaseEdges = new ArrayList<>(baseEdgesFwdReversed.size() + baseEdgesBwd.size());
        allBaseEdges.addAll(baseEdgesFwdReversed);
        allBaseEdges.addAll(baseEdgesBwd);

        // Build node sequence by walking original edges.
        List<Integer> nodes = new ArrayList<>(allBaseEdges.size() + 1);
        nodes.add(source);
        for (int e : allBaseEdges) nodes.add(g.target(e));

        // Geometry + distance.
        List<double[]> geom = new ArrayList<>(nodes.size());
        for (int v : nodes) geom.add(new double[]{ g.lat(v), g.lon(v) });
        double meters = 0.0;
        for (int e : allBaseEdges) meters += g.lengthMeters(e);

        double duration = distF[meeting] + distB[meeting];
        return new RouteResult(true, List.copyOf(nodes), geom,
                meters, duration, name(), elapsedMs, settled);
    }

    /**
     * Iteratively unpack edge {@code eid} into a list of original-edge IDs.
     * <p>
     * Shortcuts form a binary tree (each = lowerA + lowerB); recursive unpacking
     * could blow the stack on long paths, so we use an explicit stack.
     *
     * @param reverse when {@code true}, the output is in reverse traversal order
     *                (for the forward-half walk, which goes meeting → source).
     */
    private void unpackInto(int eid, List<Integer> out, boolean reverse) {
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        stack.push(eid);
        while (!stack.isEmpty()) {
            int e = stack.pop();
            if (!ch.isShortcut(e)) {
                out.add(e);
                continue;
            }
            // Order matters: lowerA is the first half (source side), lowerB the second.
            // We want to emit lowerA then lowerB in forward order. With an explicit stack
            // (LIFO), push them in REVERSE for forward order. For the reverse output
            // (the forward-half walk that traverses meeting → source), we push in the
            // natural order so lowerB comes first.
            int a = ch.shortcutLowerA(e);
            int b = ch.shortcutLowerB(e);
            if (reverse) {
                stack.push(a);
                stack.push(b);
            } else {
                stack.push(b);
                stack.push(a);
            }
        }
    }
}
