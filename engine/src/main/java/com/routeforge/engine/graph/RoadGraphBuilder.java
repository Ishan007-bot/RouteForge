package com.routeforge.engine.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-phase builder for {@link RoadGraph}.
 * <p>
 * Phase A: callers register nodes and edges in arbitrary order via
 * {@link #addNode(double, double)} and {@link #addEdge}.
 * <p>
 * Phase B: {@link #build()} sorts edges by source node and materializes
 * the CSR arrays. After {@link #build()} the builder must not be reused.
 *
 * <h3>Why two phases?</h3>
 * CSR requires that all of a node's edges be contiguous in the edge arrays.
 * We don't know the final edge ordering until we've seen all edges, so we
 * collect them in a temporary list and sort at the end.
 */
public final class RoadGraphBuilder {

    // Per-node data, grown on demand.
    private final List<Double> lat = new ArrayList<>();
    private final List<Double> lon = new ArrayList<>();

    // Per-edge data, collected then sorted at build time.
    private final List<RawEdge> rawEdges = new ArrayList<>();

    private boolean built = false;

    /** Append a node and return its internal index. */
    public int addNode(double latDeg, double lonDeg) {
        checkNotBuilt();
        lat.add(latDeg);
        lon.add(lonDeg);
        return lat.size() - 1;
    }

    /**
     * Add a directed edge from {@code source} to {@code target}.
     * For a two-way street, call this twice (once in each direction).
     *
     * @param source        node index returned by {@link #addNode}
     * @param target        node index returned by {@link #addNode}
     * @param lengthMeters  edge length (typically Haversine between the two nodes)
     * @param highwayClass  road category, used by profiles
     * @param maxSpeedKmh   value of the {@code maxspeed} OSM tag in km/h, or 0 if unset
     */
    public void addEdge(int source, int target, double lengthMeters,
                        HighwayClass highwayClass, int maxSpeedKmh) {
        checkNotBuilt();
        if (source < 0 || source >= lat.size()) {
            throw new IndexOutOfBoundsException("source node: " + source);
        }
        if (target < 0 || target >= lat.size()) {
            throw new IndexOutOfBoundsException("target node: " + target);
        }
        if (lengthMeters < 0) {
            throw new IllegalArgumentException("lengthMeters must be >= 0, got " + lengthMeters);
        }
        if (maxSpeedKmh < 0 || maxSpeedKmh > Short.MAX_VALUE) {
            throw new IllegalArgumentException("maxSpeedKmh out of range: " + maxSpeedKmh);
        }
        rawEdges.add(new RawEdge(source, target, lengthMeters,
                (byte) highwayClass.ordinal(), (short) maxSpeedKmh));
    }

    /** Number of nodes added so far. */
    public int nodeCount() { return lat.size(); }

    /** Number of edges added so far. */
    public int edgeCount() { return rawEdges.size(); }

    /**
     * Materialize the CSR arrays and return an immutable {@link RoadGraph}.
     * The builder must not be used after this call.
     */
    public RoadGraph build() {
        checkNotBuilt();
        built = true;

        int n = lat.size();
        int m = rawEdges.size();

        // Copy node arrays.
        double[] nodeLat = new double[n];
        double[] nodeLon = new double[n];
        for (int i = 0; i < n; i++) {
            nodeLat[i] = lat.get(i);
            nodeLon[i] = lon.get(i);
        }

        // Sort edges by source so all of one node's edges sit together.
        // Stable sort keeps insertion order within a source — easier to reason about.
        rawEdges.sort((a, b) -> Integer.compare(a.source, b.source));

        // Build edgeOffsets via a counting pass:
        //   first count edges per source, then a running sum gives offsets.
        int[] edgeOffsets = new int[n + 1];
        for (RawEdge e : rawEdges) {
            edgeOffsets[e.source + 1]++;
        }
        for (int i = 1; i <= n; i++) {
            edgeOffsets[i] += edgeOffsets[i - 1];
        }

        // Now scatter raw edges into the final CSR arrays.
        int[]    edgeTargets       = new int[m];
        double[] edgeLengthsMeters = new double[m];
        byte[]   edgeHighwayClass  = new byte[m];
        short[]  edgeMaxSpeedKmh   = new short[m];

        // Because rawEdges is sorted by source and edgeOffsets[i] gives the start,
        // walking rawEdges in order fills CSR perfectly without any cursor array.
        for (int i = 0; i < m; i++) {
            RawEdge e = rawEdges.get(i);
            edgeTargets[i]       = e.target;
            edgeLengthsMeters[i] = e.lengthMeters;
            edgeHighwayClass[i]  = e.highwayClass;
            edgeMaxSpeedKmh[i]   = e.maxSpeedKmh;
        }

        // Build the reverse adjacency. Same counting/scatter pattern.
        int[] inEdgeOffsets = new int[n + 1];
        for (int i = 0; i < m; i++) {
            inEdgeOffsets[edgeTargets[i] + 1]++;
        }
        for (int i = 1; i <= n; i++) {
            inEdgeOffsets[i] += inEdgeOffsets[i - 1];
        }
        int[] inEdgeSources = new int[m];
        int[] inEdgeForwardIndex = new int[m];
        // We need a cursor since we scatter in arbitrary order.
        int[] cursor = inEdgeOffsets.clone();
        // We must derive source-of-forward-edge from the sorted edge list.
        // Walk forward edges in order: rawEdges[i] has source = the same source
        // we used above, target = edgeTargets[i].
        for (int i = 0; i < m; i++) {
            int target = edgeTargets[i];
            int slot = cursor[target]++;
            inEdgeSources[slot] = rawEdges.get(i).source;
            inEdgeForwardIndex[slot] = i;
        }

        return new RoadGraph(nodeLat, nodeLon, edgeOffsets,
                edgeTargets, edgeLengthsMeters, edgeHighwayClass, edgeMaxSpeedKmh,
                inEdgeOffsets, inEdgeSources, inEdgeForwardIndex);
    }

    private void checkNotBuilt() {
        if (built) throw new IllegalStateException("Builder has already been built");
    }

    /** Temporary holder used during construction. */
    private record RawEdge(int source, int target, double lengthMeters,
                           byte highwayClass, short maxSpeedKmh) { }

    @Override
    public String toString() {
        return "RoadGraphBuilder{nodes=" + lat.size()
                + ", edges=" + rawEdges.size()
                + ", built=" + built + '}';
    }
}
