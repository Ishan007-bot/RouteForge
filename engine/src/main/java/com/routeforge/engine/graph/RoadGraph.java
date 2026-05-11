package com.routeforge.engine.graph;

import com.routeforge.engine.geom.Haversine;

/**
 * Immutable directed road graph stored in Compressed Sparse Row (CSR) layout.
 *
 * <h2>What is CSR?</h2>
 * A naive graph stores each node as an object with a {@code List<Edge>}.
 * That works but is slow: every neighbor access is a pointer chase, every
 * edge is a small object with header overhead, and the data is scattered
 * across the heap so the CPU cache is constantly missing.
 * <p>
 * CSR stores the whole graph in a handful of flat primitive arrays:
 * <pre>
 *   nodes:      0   1   2   3
 *   edgeOffsets[0..n] : index into edge arrays where node i's edges start
 *   edgeTargets[]     : target node for each edge
 *   edgeLengths[]     : length in meters for each edge
 *   edgeClass[]       : HighwayClass ordinal for each edge (1 byte)
 *   edgeMaxSpeed[]    : maxspeed tag in km/h, 0 if unset
 * </pre>
 * To iterate node {@code u}'s out-edges:
 * <pre>
 *   for (int e = edgeOffsets[u]; e &lt; edgeOffsets[u+1]; e++) {
 *       int target = edgeTargets[e];
 *       // ...
 *   }
 * </pre>
 * Why this is fast: arrays are contiguous, no boxing, no pointer chasing,
 * the CPU prefetcher loves linear access. This single decision is what
 * makes our engine outperform the original repo's pointer-graph design.
 *
 * <h2>Build via {@link RoadGraphBuilder}</h2>
 * This class is immutable. To construct a graph, use the builder.
 */
public final class RoadGraph {

    // --- Per-node arrays (size n) ---
    private final double[] nodeLat;
    private final double[] nodeLon;

    // --- CSR offsets (size n+1) ---
    // edgeOffsets[i]   = first edge index of node i
    // edgeOffsets[n]   = total number of edges (sentinel)
    private final int[] edgeOffsets;

    // --- Per-edge arrays (size m) ---
    private final int[]   edgeTargets;
    private final double[] edgeLengthsMeters;
    private final byte[]   edgeHighwayClass;   // HighwayClass.ordinal()
    private final short[]  edgeMaxSpeedKmh;    // 0 = unset

    // --- Reverse adjacency (incoming edges, size n+1 and m respectively) ---
    // Lets us iterate "who has an edge into v?", needed by Bidirectional Dijkstra
    // and by any backward graph search.
    private final int[] inEdgeOffsets;            // first incoming-edge slot for each node
    private final int[] inEdgeSources;            // source node for each incoming edge
    private final int[] inEdgeForwardIndex;       // index into edgeTargets/lengths/etc.
                                                  // (so cost/access lookups stay one place)

    // Cached HighwayClass.values() so we don't re-allocate the array on every call.
    private static final HighwayClass[] CLASSES = HighwayClass.values();

    RoadGraph(double[] nodeLat, double[] nodeLon,
              int[] edgeOffsets, int[] edgeTargets,
              double[] edgeLengthsMeters, byte[] edgeHighwayClass, short[] edgeMaxSpeedKmh,
              int[] inEdgeOffsets, int[] inEdgeSources, int[] inEdgeForwardIndex) {
        this.nodeLat = nodeLat;
        this.nodeLon = nodeLon;
        this.edgeOffsets = edgeOffsets;
        this.edgeTargets = edgeTargets;
        this.edgeLengthsMeters = edgeLengthsMeters;
        this.edgeHighwayClass = edgeHighwayClass;
        this.edgeMaxSpeedKmh = edgeMaxSpeedKmh;
        this.inEdgeOffsets = inEdgeOffsets;
        this.inEdgeSources = inEdgeSources;
        this.inEdgeForwardIndex = inEdgeForwardIndex;
    }

    // --- Node accessors ---

    public int nodeCount() { return nodeLat.length; }
    public double lat(int nodeIndex) { return nodeLat[nodeIndex]; }
    public double lon(int nodeIndex) { return nodeLon[nodeIndex]; }

    // --- Edge accessors ---

    public int edgeCount() { return edgeTargets.length; }

    /** Index of the first out-edge of {@code nodeIndex} in the edge arrays. */
    public int firstEdge(int nodeIndex) { return edgeOffsets[nodeIndex]; }

    /** One past the last out-edge of {@code nodeIndex}. */
    public int endEdge(int nodeIndex) { return edgeOffsets[nodeIndex + 1]; }

    public int target(int edgeIndex) { return edgeTargets[edgeIndex]; }
    public double lengthMeters(int edgeIndex) { return edgeLengthsMeters[edgeIndex]; }
    public HighwayClass highwayClass(int edgeIndex) { return CLASSES[edgeHighwayClass[edgeIndex]]; }
    /** {@code 0} if the source way had no {@code maxspeed} tag. */
    public int maxSpeedKmh(int edgeIndex) { return edgeMaxSpeedKmh[edgeIndex] & 0xFFFF; }

    // --- Reverse adjacency accessors (incoming edges of a node) ---

    public int firstInEdge(int nodeIndex) { return inEdgeOffsets[nodeIndex]; }
    public int endInEdge(int nodeIndex)   { return inEdgeOffsets[nodeIndex + 1]; }
    /** Source node of the i-th incoming edge (i is an index into the in-edge arrays). */
    public int inEdgeSource(int inEdgeIndex) { return inEdgeSources[inEdgeIndex]; }
    /**
     * Forward edge index for this incoming edge. Use this to call
     * {@link #lengthMeters}, {@link #highwayClass}, {@link #maxSpeedKmh},
     * and the profile's {@code cost} / {@code allowed} methods.
     */
    public int inEdgeForwardIndex(int inEdgeIndex) { return inEdgeForwardIndex[inEdgeIndex]; }

    // --- Convenience ---

    /**
     * Find the graph node closest to the given lat/lon, in great-circle distance.
     * Linear scan — fine for Phase 1 (city-sized graphs). Phase 2 will add a spatial index.
     *
     * @return node index, or {@code -1} if the graph is empty
     */
    public int nearestNode(double lat, double lon) {
        int n = nodeCount();
        if (n == 0) return -1;
        int best = 0;
        double bestDist = Haversine.distanceMeters(lat, lon, nodeLat[0], nodeLon[0]);
        for (int i = 1; i < n; i++) {
            double d = Haversine.distanceMeters(lat, lon, nodeLat[i], nodeLon[i]);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }
}
