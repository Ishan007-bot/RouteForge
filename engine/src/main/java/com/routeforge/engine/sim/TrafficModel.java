package com.routeforge.engine.sim;

import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Live traffic conditions on a road graph.
 *
 * <p>Two arrays sized to the graph's edge count:
 * <ul>
 *   <li><b>load</b> — number of vehicles currently traversing the edge</li>
 *   <li><b>closed</b> — a one-bit flag making the edge impassable</li>
 * </ul>
 *
 * <p>The cost of an edge is scaled by a BPR (Bureau of Public Roads) factor:
 * <pre>
 *   factor = 1 + alpha * (load / capacity)^beta
 * </pre>
 * with the canonical {@code alpha=0.15}, {@code beta=4}. Light load is
 * essentially free-flow; load near or above capacity grows quartically.
 *
 * <p>Closed edges return infinity from {@link #congestionFactor} so a
 * traffic-aware profile sees them as impassable and re-routes around.
 *
 * <p>{@code load} mutations are atomic so the tick thread and reroute
 * paths can update counts without coarse locking. The {@code closed}
 * flag is intentionally {@code volatile boolean[]} — single-writer
 * (the tick thread), many-reader.
 */
public final class TrafficModel {

    public static final double ALPHA = 0.15;
    public static final double BETA  = 4.0;

    /** Vehicles per edge before serious congestion kicks in. */
    private static final int DEFAULT_CAPACITY = 8;

    private final AtomicIntegerArray load;
    private final boolean[] closed;
    private final int capacity;

    public TrafficModel(int edgeCount) {
        this(edgeCount, DEFAULT_CAPACITY);
    }

    public TrafficModel(int edgeCount, int capacity) {
        this.load     = new AtomicIntegerArray(edgeCount);
        this.closed   = new boolean[edgeCount];
        this.capacity = capacity;
    }

    public int  load(int edge)   { return load.get(edge); }
    public void enter(int edge)  { load.incrementAndGet(edge); }
    public void leave(int edge)  { if (load.get(edge) > 0) load.decrementAndGet(edge); }

    public boolean isClosed(int edge) { return closed[edge]; }
    public void    close(int edge)    { closed[edge] = true; }
    public void    open(int edge)     { closed[edge] = false; }

    /** Multiplicative factor applied to the edge's free-flow cost. */
    public double congestionFactor(int edge) {
        if (closed[edge]) return Double.POSITIVE_INFINITY;
        int n = load.get(edge);
        if (n <= 0) return 1.0;
        double ratio = (double) n / capacity;
        return 1.0 + ALPHA * Math.pow(ratio, BETA);
    }

    public int edgeCount() { return load.length(); }
    public int capacity()  { return capacity; }
}
