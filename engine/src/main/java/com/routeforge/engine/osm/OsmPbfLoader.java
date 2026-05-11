package com.routeforge.engine.osm;

import crosby.binary.osmosis.OsmosisReader;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Loads an OpenStreetMap {@code .osm.pbf} file and reports counts of the
 * top-level element types (nodes, ways, relations).
 * <p>
 * Phase 0 scope: counts only. The loader will gain real graph-building
 * responsibilities in Phase 1.
 *
 * <h3>How OSM data is structured</h3>
 * An OSM file is a stream of three element types:
 * <ul>
 *   <li><b>Nodes</b> — geographic points (lat/lon), e.g. an intersection or POI.</li>
 *   <li><b>Ways</b>   — ordered lists of nodes that form lines or polygons
 *                       (a road, a building outline, a river).</li>
 *   <li><b>Relations</b> — groups of nodes/ways/relations with a semantic role
 *                          (a bus route, a turn restriction, a multipolygon).</li>
 * </ul>
 * Each element has an {@code id} and a bag of free-form {@code key=value} tags
 * (e.g. {@code highway=residential}, {@code maxspeed=30}).
 *
 * <h3>How the parser works</h3>
 * Osmosis uses the <i>sink</i> (a.k.a. visitor) pattern: the reader walks the
 * file and pushes every entity into a {@link Sink}. We supply a sink that
 * just increments counters.
 */
public final class OsmPbfLoader {

    private static final Logger log = LoggerFactory.getLogger(OsmPbfLoader.class);

    /**
     * Parse the given {@code .osm.pbf} file and return element counts.
     *
     * @param pbfFile path to the file (must exist and be readable)
     * @throws IOException                if the file can't be read or is malformed
     * @throws IllegalArgumentException   if {@code pbfFile} doesn't refer to a regular file
     * @throws NullPointerException       if {@code pbfFile} is null
     */
    public OsmStats load(Path pbfFile) throws IOException {
        Objects.requireNonNull(pbfFile, "pbfFile must not be null");
        if (!Files.isRegularFile(pbfFile)) {
            throw new IllegalArgumentException("Not a regular file: " + pbfFile);
        }

        log.info("Loading OSM PBF: {} ({} bytes)", pbfFile, Files.size(pbfFile));
        long start = System.nanoTime();

        // BufferedInputStream wraps the raw file stream so the PBF reader
        // doesn't make a syscall per byte — big speedup for binary parsing.
        try (InputStream in = new BufferedInputStream(Files.newInputStream(pbfFile))) {
            CountingSink sink = new CountingSink();

            // OsmosisReader walks the file once, top to bottom, calling
            // sink.process(...) for each entity it finds. It's a Runnable
            // (designed for Osmosis's pipeline), so we call run() directly.
            OsmosisReader reader = new OsmosisReader(in);
            reader.setSink(sink);
            try {
                reader.run();
            } catch (RuntimeException e) {
                // Osmosis wraps low-level I/O / format errors in unchecked
                // exceptions. Re-throw as IOException so callers can handle
                // them uniformly with file-not-found etc.
                throw new IOException("Failed to parse PBF: " + pbfFile, e);
            }

            OsmStats stats = sink.toStats();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.info("Loaded: {} (took {} ms)", stats, elapsedMs);
            return stats;
        }
    }

    /** Sink that just counts each kind of entity. */
    private static final class CountingSink implements Sink {
        private long nodes;
        private long ways;
        private long relations;

        @Override
        public void initialize(Map<String, Object> metaData) {
            // No initialization needed.
        }

        @Override
        public void process(EntityContainer entityContainer) {
            EntityType type = entityContainer.getEntity().getType();
            switch (type) {
                case Node     -> nodes++;
                case Way      -> ways++;
                case Relation -> relations++;
                case Bound    -> { /* bounding box header — ignore */ }
            }
        }

        @Override public void complete() { /* nothing to flush */ }
        @Override public void close()    { /* nothing to release */ }

        OsmStats toStats() {
            return new OsmStats(nodes, ways, relations);
        }
    }

    /**
     * CLI entry point for manual testing.
     * <p>
     * Run from the project root:
     * <pre>
     *   .\mvnw.cmd -pl engine compile exec:java \
     *       "-Dexec.mainClass=com.routeforge.engine.osm.OsmPbfLoader" \
     *       "-Dexec.args=data/liechtenstein-latest.osm.pbf"
     * </pre>
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: OsmPbfLoader <path-to-osm.pbf>");
            System.exit(64); // EX_USAGE
        }
        OsmStats stats = new OsmPbfLoader().load(Path.of(args[0]));
        System.out.printf("nodes=%,d  ways=%,d  relations=%,d  total=%,d%n",
                stats.nodes(), stats.ways(), stats.relations(), stats.total());
    }
}
