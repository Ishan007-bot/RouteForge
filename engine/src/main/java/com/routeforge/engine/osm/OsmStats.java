package com.routeforge.engine.osm;

/**
 * Counts of the top-level OSM elements found in a file.
 * <p>
 * A Java {@code record} is a concise way to declare an immutable
 * data carrier — the compiler generates the constructor, accessors,
 * {@code equals}, {@code hashCode}, and {@code toString} for us.
 */
public record OsmStats(long nodes, long ways, long relations) {

    public long total() {
        return nodes + ways + relations;
    }
}
