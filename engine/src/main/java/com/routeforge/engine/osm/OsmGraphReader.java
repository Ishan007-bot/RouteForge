package com.routeforge.engine.osm;

import com.routeforge.engine.geom.Haversine;
import com.routeforge.engine.graph.HighwayClass;
import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.graph.RoadGraphBuilder;
import crosby.binary.osmosis.OsmosisReader;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads an OSM {@code .osm.pbf} file and produces a {@link RoadGraph}.
 *
 * <h2>Why this is a two-pass parse</h2>
 * Each OSM way references node ids, but the way and its nodes can appear
 * in any order in the file (in practice nodes always come first in PBFs,
 * but we don't rely on that). So we:
 * <ol>
 *   <li>Pass 1: stream the file once, remembering every node's lat/lon
 *       and every <i>road</i> way (one with a {@code highway=*} tag).</li>
 *   <li>Pass 2: for each road way, look up the coordinates of its nodes,
 *       emit edges into the {@link RoadGraphBuilder}.</li>
 * </ol>
 * Both "passes" happen inside the single Osmosis read here — pass 2 is in
 * {@link Sink#complete} after we've seen everything.
 *
 * <h2>Notes / Phase 1 limitations</h2>
 * <ul>
 *   <li>Stores <b>all</b> node coordinates in a {@link HashMap} even if the
 *       node turns out not to be on a road. Fine for city-sized extracts;
 *       a future optimization is a streaming two-pass over the file.</li>
 *   <li>Ignores OSM relations (turn restrictions, multipolygons).
 *       Turn restrictions become important in Phase 2.</li>
 *   <li>Honors only {@code highway}, {@code oneway}, and {@code maxspeed} tags.
 *       Doesn't yet read {@code access=no}, {@code bicycle=no}, etc.</li>
 * </ul>
 */
public final class OsmGraphReader {

    private static final Logger log = LoggerFactory.getLogger(OsmGraphReader.class);

    public RoadGraph read(Path pbfFile) throws IOException {
        Objects.requireNonNull(pbfFile, "pbfFile must not be null");
        if (!Files.isRegularFile(pbfFile)) {
            throw new IllegalArgumentException("Not a regular file: " + pbfFile);
        }

        log.info("Reading OSM PBF into graph: {} ({} bytes)", pbfFile, Files.size(pbfFile));
        long startNs = System.nanoTime();

        try (InputStream in = new BufferedInputStream(Files.newInputStream(pbfFile))) {
            BuilderSink sink = new BuilderSink();
            OsmosisReader reader = new OsmosisReader(in);
            reader.setSink(sink);
            try {
                reader.run();
            } catch (RuntimeException e) {
                throw new IOException("Failed to parse PBF: " + pbfFile, e);
            }
            RoadGraph graph = sink.buildGraph();
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("Built graph: {} nodes, {} edges (took {} ms)",
                    graph.nodeCount(), graph.edgeCount(), elapsedMs);
            return graph;
        }
    }

    /** Osmosis sink that captures node coordinates and road-way descriptors. */
    private static final class BuilderSink implements Sink {

        // OSM id -> [lat, lon]. Stores every node we see.
        // For country-scale graphs we'd swap this for a fastutil primitive map.
        private final Map<Long, double[]> nodeCoords = new HashMap<>(1 << 17);

        // One entry per road way captured during streaming.
        private final List<RawWay> roadWays = new ArrayList<>();

        @Override public void initialize(Map<String, Object> metaData) { /* no-op */ }

        @Override
        public void process(EntityContainer ec) {
            switch (ec.getEntity()) {
                case Node n -> nodeCoords.put(n.getId(),
                        new double[]{ n.getLatitude(), n.getLongitude() });
                case Way w -> handleWay(w);
                default    -> { /* relations and bounds: ignore */ }
            }
        }

        private void handleWay(Way w) {
            String highway = null;
            String maxspeed = null;
            boolean oneway = false;
            for (Tag t : w.getTags()) {
                switch (t.getKey()) {
                    case "highway"  -> highway = t.getValue();
                    case "maxspeed" -> maxspeed = t.getValue();
                    case "oneway"   -> oneway = isOneway(t.getValue());
                    default         -> { /* not interested */ }
                }
            }
            if (highway == null) return;

            // Snapshot node ids so we don't retain the Way object.
            List<WayNode> wn = w.getWayNodes();
            long[] nodeIds = new long[wn.size()];
            for (int i = 0; i < wn.size(); i++) nodeIds[i] = wn.get(i).getNodeId();

            roadWays.add(new RawWay(nodeIds, HighwayClass.fromOsmTag(highway),
                    parseMaxSpeed(maxspeed), oneway));
        }

        @Override public void complete() { /* graph assembled lazily in buildGraph() */ }
        @Override public void close()    { /* nothing to release */ }

        RoadGraph buildGraph() {
            RoadGraphBuilder b = new RoadGraphBuilder();
            // OSM node id -> internal node index. Only nodes referenced by a road get one.
            Map<Long, Integer> osmToInternal = new HashMap<>(roadWays.size() * 4);

            int orphanWays = 0;
            for (RawWay way : roadWays) {
                int[] internal = new int[way.nodeIds.length];
                double[][] coords = new double[way.nodeIds.length][];
                boolean orphan = false;
                for (int i = 0; i < way.nodeIds.length; i++) {
                    long osmId = way.nodeIds[i];
                    double[] ll = nodeCoords.get(osmId);
                    if (ll == null) {
                        // Way references a node not in the file (cropped extract).
                        orphan = true;
                        break;
                    }
                    coords[i] = ll;
                    Integer existing = osmToInternal.get(osmId);
                    if (existing != null) { internal[i] = existing; continue; }
                    int idx = b.addNode(ll[0], ll[1]);
                    osmToInternal.put(osmId, idx);
                    internal[i] = idx;
                }
                if (orphan) { orphanWays++; continue; }

                // Add edges between consecutive nodes in the way.
                for (int i = 0; i < internal.length - 1; i++) {
                    double meters = Haversine.distanceMeters(
                            coords[i][0], coords[i][1],
                            coords[i + 1][0], coords[i + 1][1]);
                    b.addEdge(internal[i], internal[i + 1], meters, way.highway, way.maxSpeedKmh);
                    if (!way.oneway) {
                        b.addEdge(internal[i + 1], internal[i], meters, way.highway, way.maxSpeedKmh);
                    }
                }
            }
            if (orphanWays > 0) {
                log.info("Skipped {} ways referencing missing nodes (incomplete extract)", orphanWays);
            }

            // Free the parse-time maps before we build the immutable graph.
            nodeCoords.clear();
            roadWays.clear();

            return b.build();
        }
    }

    // ---------- Tag parsing ----------

    private static boolean isOneway(String v) {
        if (v == null) return false;
        return switch (v.toLowerCase()) {
            case "yes", "true", "1" -> true;
            default -> false;
        };
    }

    /**
     * Parse an OSM {@code maxspeed} tag value to km/h, ignoring unsupported forms.
     * Handles plain numbers, "50 mph", "50 km/h". Returns 0 if unparseable
     * so callers fall back to the highway-class default.
     */
    static int parseMaxSpeed(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        String s = raw.trim().toLowerCase();
        boolean mph = false;
        if (s.endsWith(" mph")) { mph = true; s = s.substring(0, s.length() - 4).trim(); }
        else if (s.endsWith("mph")) { mph = true; s = s.substring(0, s.length() - 3).trim(); }
        else if (s.endsWith(" km/h")) { s = s.substring(0, s.length() - 5).trim(); }
        else if (s.endsWith("km/h")) { s = s.substring(0, s.length() - 4).trim(); }
        try {
            int n = Integer.parseInt(s);
            if (n <= 0 || n > 300) return 0;
            return mph ? (int) Math.round(n * 1.609344) : n;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Captured while streaming; processed in {@link BuilderSink#buildGraph()}. */
    private record RawWay(long[] nodeIds, HighwayClass highway, int maxSpeedKmh, boolean oneway) { }
}
