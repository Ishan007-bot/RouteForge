package com.routeforge.engine.ch;

import com.routeforge.engine.graph.RoadGraph;

/**
 * Immutable result of CH preprocessing.
 *
 * <h2>Edge namespace</h2>
 * We use a single integer space for "edges":
 * <ul>
 *   <li>Indices in {@code [0, originalEdgeCount)} refer to original
 *       {@link RoadGraph} edges (forward direction). Look up cost / target via
 *       the underlying {@link RoadGraph}.</li>
 *   <li>Indices in {@code [originalEdgeCount, originalEdgeCount + shortcutCount)}
 *       are <i>shortcuts</i>. Each shortcut bypasses one contracted node and
 *       remembers the two underlying edge IDs ({@code lowerA}, {@code lowerB})
 *       — themselves either original edges or further shortcuts. This lets the
 *       query unpack a route back to a sequence of original edges.</li>
 * </ul>
 *
 * <h2>Upward CSR adjacencies</h2>
 * For the bidirectional upward query we need to iterate, for each node:
 * <ul>
 *   <li><b>forward upward</b>: out-edges to higher-level neighbors;</li>
 *   <li><b>backward upward</b>: in-edges <i>from</i> higher-level neighbors —
 *       equivalent to forward-up edges in the reverse graph.</li>
 * </ul>
 * Both are stored as CSR arrays over the unified edge-ID space.
 */
public final class CHGraph {

    private final RoadGraph base;
    private final int[] nodeLevel;

    // ----- Shortcut storage (indexed by shortcut number, 0-based) -----
    private final int[]    scSource;
    private final int[]    scTarget;
    private final double[] scCost;
    private final int[]    scLowerA;     // first  underlying edge ID
    private final int[]    scLowerB;     // second underlying edge ID

    // ----- Upward forward adjacency -----
    private final int[] upFwdOffsets;      // size n+1
    private final int[] upFwdEdgeIds;      // unified edge IDs

    // ----- Upward backward adjacency (incoming "upward" edges, used by backward search) -----
    private final int[] upBwdOffsets;      // size n+1
    private final int[] upBwdEdgeIds;      // unified edge IDs

    public CHGraph(RoadGraph base, int[] nodeLevel,
                   int[] scSource, int[] scTarget, double[] scCost,
                   int[] scLowerA, int[] scLowerB,
                   int[] upFwdOffsets, int[] upFwdEdgeIds,
                   int[] upBwdOffsets, int[] upBwdEdgeIds) {
        this.base = base;
        this.nodeLevel = nodeLevel;
        this.scSource = scSource;
        this.scTarget = scTarget;
        this.scCost = scCost;
        this.scLowerA = scLowerA;
        this.scLowerB = scLowerB;
        this.upFwdOffsets = upFwdOffsets;
        this.upFwdEdgeIds = upFwdEdgeIds;
        this.upBwdOffsets = upBwdOffsets;
        this.upBwdEdgeIds = upBwdEdgeIds;
    }

    public RoadGraph base() { return base; }
    public int nodeCount() { return base.nodeCount(); }
    public int originalEdgeCount() { return base.edgeCount(); }
    public int shortcutCount() { return scSource.length; }
    public int level(int node) { return nodeLevel[node]; }

    /** True if an "edge ID" refers to a shortcut rather than a base edge. */
    public boolean isShortcut(int edgeId) { return edgeId >= base.edgeCount(); }
    private int shortcutIndex(int edgeId) { return edgeId - base.edgeCount(); }

    /** Source node of any edge in the unified namespace. */
    public int edgeSource(int edgeId) {
        if (!isShortcut(edgeId)) return base.source(edgeId);
        return scSource[shortcutIndex(edgeId)];
    }

    /** Target node of any edge in the unified namespace. */
    public int edgeTarget(int edgeId) {
        if (!isShortcut(edgeId)) return base.target(edgeId);
        return scTarget[shortcutIndex(edgeId)];
    }

    /** Cost of any edge — base cost from the profile, shortcut cost precomputed. */
    public double edgeCost(int edgeId, com.routeforge.engine.profile.Profile p) {
        if (!isShortcut(edgeId)) return p.cost(base, edgeId);
        return scCost[shortcutIndex(edgeId)];
    }

    /** Two underlying edge IDs for a shortcut (in source→target traversal order). */
    public int shortcutLowerA(int edgeId) { return scLowerA[shortcutIndex(edgeId)]; }
    public int shortcutLowerB(int edgeId) { return scLowerB[shortcutIndex(edgeId)]; }

    public int firstUpFwd(int node) { return upFwdOffsets[node]; }
    public int endUpFwd(int node)   { return upFwdOffsets[node + 1]; }
    public int upFwdEdgeId(int slot) { return upFwdEdgeIds[slot]; }

    public int firstUpBwd(int node) { return upBwdOffsets[node]; }
    public int endUpBwd(int node)   { return upBwdOffsets[node + 1]; }
    public int upBwdEdgeId(int slot) { return upBwdEdgeIds[slot]; }
}
