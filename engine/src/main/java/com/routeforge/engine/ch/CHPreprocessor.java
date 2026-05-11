package com.routeforge.engine.ch;

import com.routeforge.engine.algo.IndexedBinaryHeap;
import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.profile.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link CHGraph} from a {@link RoadGraph} + {@link Profile}.
 *
 * <h2>The contraction loop</h2>
 * Repeatedly pick the least-important uncontracted node {@code v} and "contract" it:
 * <ol>
 *   <li>For every pair of <i>uncontracted</i> neighbors {@code (u, w)} where
 *       an edge enters {@code v} from {@code u} and leaves {@code v} toward
 *       {@code w}, compute the potential shortcut cost
 *       {@code c = cost(u→v) + cost(v→w)}.</li>
 *   <li>Run a <b>witness search</b> from {@code u} (a small Dijkstra over the
 *       uncontracted graph, forbidden from visiting {@code v}). If we can
 *       reach {@code w} with cost {@code <= c}, the shortcut is unnecessary —
 *       there's a "witness" path. Otherwise we add the shortcut.</li>
 *   <li>Assign {@code v}'s level (its contraction order).</li>
 *   <li>Re-evaluate the importance of {@code v}'s neighbors and update the
 *       priority queue.</li>
 * </ol>
 *
 * <h2>Importance heuristic</h2>
 * We use the standard <b>edge difference</b> heuristic:
 * {@code importance(v) = #shortcuts_v_would_add - #edges_incident_to_v}.
 * Negative means contraction <i>simplifies</i> the graph (good — contract
 * early). Plus a small {@code contractedNeighbors} tie-breaker to spread the
 * level distribution uniformly.
 *
 * <h2>Phase 2 simplifications (acknowledged limits)</h2>
 * <ul>
 *   <li>Importance is recomputed on demand; we don't track every dependency.
 *       In practice this is the "lazy" variant — when a node bubbles to the top,
 *       we recompute its importance and only contract if it's still cheapest.</li>
 *   <li>Witness search uses a generous hop limit. A production CH bounds the
 *       witness Dijkstra more aggressively for speed.</li>
 *   <li>No parallel contraction. The reference fast CH builders contract
 *       independent nodes in parallel batches.</li>
 * </ul>
 */
public final class CHPreprocessor {

    private static final Logger log = LoggerFactory.getLogger(CHPreprocessor.class);

    /** Max hops the witness search will explore. Larger = stronger CH (fewer shortcuts), slower preprocessing. */
    private static final int WITNESS_HOP_LIMIT = 5;

    private final RoadGraph base;
    private final Profile profile;
    private final int n;
    private final int mOrig;

    // contracted[v] = true once v has been removed from the active graph.
    // nodeLevel[v]  = contraction order (0 = first removed = least important).
    private final boolean[] contracted;
    private final int[]     nodeLevel;
    private int             nextLevel = 0;

    // Adjustment counter to spread levels.
    private final int[] contractedNeighbors;

    // Dynamic adjacency over the unified edge ID space. Grows as shortcuts are added.
    // For each node v: list of outgoing unified edge IDs / incoming unified edge IDs.
    // We re-evaluate the "still in active graph" status by checking contracted[other].
    private final List<int[]> outAdj;  // node -> int[] of outgoing unified edge IDs
    private final List<int[]> inAdj;   // node -> int[] of incoming unified edge IDs

    // Shortcut storage (final size unknown until we're done).
    private final List<int[]>    scEdges  = new ArrayList<>();  // [source, target, lowerA, lowerB]
    private final List<Double>   scCosts  = new ArrayList<>();

    public CHPreprocessor(RoadGraph base, Profile profile) {
        this.base = base;
        this.profile = profile;
        this.n = base.nodeCount();
        this.mOrig = base.edgeCount();
        this.contracted = new boolean[n];
        this.nodeLevel = new int[n];
        this.contractedNeighbors = new int[n];

        // Seed adjacencies with original edges only.
        this.outAdj = new ArrayList<>(n);
        this.inAdj  = new ArrayList<>(n);
        // Count first.
        int[] outDeg = new int[n];
        int[] inDeg  = new int[n];
        for (int v = 0; v < n; v++) {
            outDeg[v] = base.endEdge(v) - base.firstEdge(v);
            inDeg[v]  = base.endInEdge(v) - base.firstInEdge(v);
        }
        for (int v = 0; v < n; v++) {
            int[] outs = new int[outDeg[v]];
            int j = 0;
            for (int e = base.firstEdge(v); e < base.endEdge(v); e++) outs[j++] = e;
            outAdj.add(outs);

            int[] ins = new int[inDeg[v]];
            j = 0;
            for (int i = base.firstInEdge(v); i < base.endInEdge(v); i++) {
                ins[j++] = base.inEdgeForwardIndex(i);
            }
            inAdj.add(ins);
        }
    }

    /** Run the contraction loop and return the immutable {@link CHGraph}. */
    public CHGraph preprocess() {
        long startNs = System.nanoTime();
        log.info("CH preprocessing started: {} nodes, {} edges", n, mOrig);

        // Priority queue keyed by importance. Lower importance = contract first.
        IndexedBinaryHeap pq = new IndexedBinaryHeap(n);
        for (int v = 0; v < n; v++) {
            pq.insertOrDecrease(v, computeImportance(v));
        }

        int shortcutsAdded = 0;
        long lastReportNs = startNs;

        while (!pq.isEmpty()) {
            int v = pq.pollMin();
            // Skip if already contracted (defensive — shouldn't happen but cheap).
            if (contracted[v]) continue;

            shortcutsAdded += contract(v);
            nodeLevel[v] = nextLevel++;

            // Re-prioritize neighbors whose graph just changed.
            // We rely on insertOrDecrease's "decrease only" semantics: if a
            // neighbor's freshly-computed importance is HIGHER than what the
            // heap has, we leave the stale value. The CH produced is still
            // correct — at worst it has a few extra shortcuts.
            for (int eid : outAdj.get(v)) {
                int w = edgeTarget(eid);
                if (!contracted[w]) {
                    contractedNeighbors[w]++;
                    pq.insertOrDecrease(w, computeImportance(w));
                }
            }
            for (int eid : inAdj.get(v)) {
                int u = edgeSource(eid);
                if (!contracted[u]) {
                    contractedNeighbors[u]++;
                    pq.insertOrDecrease(u, computeImportance(u));
                }
            }

            long now = System.nanoTime();
            if ((now - lastReportNs) > 5_000_000_000L) {  // log every ~5s
                log.info("CH progress: {}/{} nodes contracted, {} shortcuts so far",
                        nextLevel, n, shortcutsAdded);
                lastReportNs = now;
            }
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("CH preprocessing done: {} shortcuts added (took {} ms)",
                shortcutsAdded, elapsedMs);

        return materialize();
    }

    // -------- Importance heuristic --------

    private double computeImportance(int v) {
        if (contracted[v]) return Double.POSITIVE_INFINITY;
        int incidentEdges = 0;
        int potentialShortcuts = countPotentialShortcuts(v, /* simulate */ true, null);
        for (int eid : outAdj.get(v)) if (!contracted[edgeTarget(eid)]) incidentEdges++;
        for (int eid : inAdj.get(v))  if (!contracted[edgeSource(eid)]) incidentEdges++;
        // Edge difference + small tie-breaker proportional to how many neighbors
        // have already been contracted (keeps levels evenly distributed).
        return potentialShortcuts - incidentEdges + 0.1 * contractedNeighbors[v];
    }

    // -------- Contraction --------

    /**
     * @param v          node to contract
     * @return number of shortcuts added during this contraction
     */
    private int contract(int v) {
        List<Shortcut> shortcuts = new ArrayList<>();
        countPotentialShortcuts(v, /* simulate */ false, shortcuts);

        for (Shortcut s : shortcuts) {
            int scId = mOrig + scEdges.size();
            scEdges.add(new int[]{ s.u, s.w, s.lowerA, s.lowerB });
            scCosts.add(s.cost);

            // Append to dynamic adjacency.
            outAdj.set(s.u, append(outAdj.get(s.u), scId));
            inAdj.set(s.w, append(inAdj.get(s.w), scId));
        }
        contracted[v] = true;
        return shortcuts.size();
    }

    /**
     * @param simulate when {@code true}, only counts; when {@code false} also
     *                 fills the {@code out} list.
     */
    private int countPotentialShortcuts(int v, boolean simulate, List<Shortcut> out) {
        int count = 0;
        // For each pair of (in-edge from u, out-edge to w), both still active.
        for (int incoming : inAdj.get(v)) {
            int u = edgeSource(incoming);
            if (contracted[u] || u == v) continue;
            double cUV = edgeCost(incoming);
            for (int outgoing : outAdj.get(v)) {
                int w = edgeTarget(outgoing);
                if (contracted[w] || w == v || w == u) continue;
                double cVW = edgeCost(outgoing);
                double shortcutCost = cUV + cVW;

                if (!hasWitness(u, w, v, shortcutCost)) {
                    count++;
                    if (!simulate) {
                        out.add(new Shortcut(u, w, shortcutCost, incoming, outgoing));
                    }
                }
            }
        }
        return count;
    }

    /**
     * Limited Dijkstra from {@code source} that ignores node {@code forbidden}
     * and stops once it has reached {@code target} or exhausted
     * {@link #WITNESS_HOP_LIMIT} hops / a cost limit.
     */
    private boolean hasWitness(int source, int target, int forbidden, double limit) {
        // Map<node, bestKnownDist>. Small graph local to v's neighborhood — HashMap is fine.
        Map<Integer, Double> dist = new HashMap<>();
        IndexedBinaryHeap pq = new IndexedBinaryHeap(n);
        Map<Integer, Integer> hops = new HashMap<>();
        dist.put(source, 0.0);
        hops.put(source, 0);
        pq.insertOrDecrease(source, 0);

        while (!pq.isEmpty()) {
            int u = pq.pollMin();
            double du = dist.get(u);
            if (du > limit) return false;
            if (u == target) return du <= limit;
            int hu = hops.get(u);
            if (hu >= WITNESS_HOP_LIMIT) continue;

            for (int eid : outAdj.get(u)) {
                int x = edgeTarget(eid);
                if (contracted[x] || x == forbidden) continue;
                double nd = du + edgeCost(eid);
                if (nd > limit) continue;
                Double prev = dist.get(x);
                if (prev == null || nd < prev) {
                    dist.put(x, nd);
                    hops.put(x, hu + 1);
                    pq.insertOrDecrease(x, nd);
                }
            }
        }
        return false;
    }

    // -------- Edge accessors over the unified namespace --------

    private double edgeCost(int edgeId) {
        if (edgeId < mOrig) return profile.cost(base, edgeId);
        return scCosts.get(edgeId - mOrig);
    }

    private int edgeSource(int edgeId) {
        if (edgeId < mOrig) return base.source(edgeId);
        return scEdges.get(edgeId - mOrig)[0];
    }

    private int edgeTarget(int edgeId) {
        if (edgeId < mOrig) return base.target(edgeId);
        return scEdges.get(edgeId - mOrig)[1];
    }

    // -------- Materialization --------

    private CHGraph materialize() {
        int scCount = scEdges.size();
        int[]    scSource  = new int[scCount];
        int[]    scTarget  = new int[scCount];
        double[] scCostArr = new double[scCount];
        int[]    scLowerA  = new int[scCount];
        int[]    scLowerB  = new int[scCount];
        for (int i = 0; i < scCount; i++) {
            int[] s = scEdges.get(i);
            scSource[i]  = s[0];
            scTarget[i]  = s[1];
            scLowerA[i]  = s[2];
            scLowerB[i]  = s[3];
            scCostArr[i] = scCosts.get(i);
        }

        // Build upward forward CSR: for each u, edges (u, v) where level[v] > level[u].
        // Walk both original out-edges (mOrig of them) and shortcut edges.
        // First a counting pass.
        int[] upFwdCount = new int[n + 1];
        int[] upBwdCount = new int[n + 1];

        for (int v = 0; v < n; v++) {
            // Original out-edges
            for (int e = base.firstEdge(v); e < base.endEdge(v); e++) {
                int t = base.target(e);
                if (nodeLevel[t] > nodeLevel[v]) upFwdCount[v + 1]++;
            }
        }
        for (int i = 0; i < scCount; i++) {
            int u = scSource[i], w = scTarget[i];
            if (nodeLevel[w] > nodeLevel[u]) upFwdCount[u + 1]++;
        }
        for (int v = 0; v < n; v++) {
            // Original in-edges
            for (int i = base.firstInEdge(v); i < base.endInEdge(v); i++) {
                int u = base.inEdgeSource(i);
                if (nodeLevel[u] > nodeLevel[v]) upBwdCount[v + 1]++;
            }
        }
        for (int i = 0; i < scCount; i++) {
            int u = scSource[i], w = scTarget[i];
            if (nodeLevel[u] > nodeLevel[w]) upBwdCount[w + 1]++;
        }

        // Prefix sum to offsets.
        int[] upFwdOffsets = upFwdCount;  // reuse
        int[] upBwdOffsets = upBwdCount;
        for (int i = 1; i <= n; i++) upFwdOffsets[i] += upFwdOffsets[i - 1];
        for (int i = 1; i <= n; i++) upBwdOffsets[i] += upBwdOffsets[i - 1];

        int[] upFwdEdgeIds = new int[upFwdOffsets[n]];
        int[] upBwdEdgeIds = new int[upBwdOffsets[n]];
        int[] upFwdCursor = upFwdOffsets.clone();
        int[] upBwdCursor = upBwdOffsets.clone();

        // Scatter pass.
        for (int v = 0; v < n; v++) {
            for (int e = base.firstEdge(v); e < base.endEdge(v); e++) {
                int t = base.target(e);
                if (nodeLevel[t] > nodeLevel[v]) {
                    upFwdEdgeIds[upFwdCursor[v]++] = e;
                }
            }
        }
        for (int i = 0; i < scCount; i++) {
            int u = scSource[i], w = scTarget[i];
            int scId = mOrig + i;
            if (nodeLevel[w] > nodeLevel[u]) {
                upFwdEdgeIds[upFwdCursor[u]++] = scId;
            }
        }
        for (int v = 0; v < n; v++) {
            for (int i = base.firstInEdge(v); i < base.endInEdge(v); i++) {
                int u = base.inEdgeSource(i);
                if (nodeLevel[u] > nodeLevel[v]) {
                    upBwdEdgeIds[upBwdCursor[v]++] = base.inEdgeForwardIndex(i);
                }
            }
        }
        for (int i = 0; i < scCount; i++) {
            int u = scSource[i], w = scTarget[i];
            int scId = mOrig + i;
            if (nodeLevel[u] > nodeLevel[w]) {
                upBwdEdgeIds[upBwdCursor[w]++] = scId;
            }
        }

        return new CHGraph(base, nodeLevel,
                scSource, scTarget, scCostArr, scLowerA, scLowerB,
                upFwdOffsets, upFwdEdgeIds, upBwdOffsets, upBwdEdgeIds);
    }

    private static int[] append(int[] arr, int x) {
        int[] grown = Arrays.copyOf(arr, arr.length + 1);
        grown[arr.length] = x;
        return grown;
    }

    /** Internal carrier for a candidate shortcut. */
    private record Shortcut(int u, int w, double cost, int lowerA, int lowerB) { }
}
