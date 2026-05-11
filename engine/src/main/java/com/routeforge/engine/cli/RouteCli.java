package com.routeforge.engine.cli;

import com.routeforge.engine.osm.OsmGraphReader;
import com.routeforge.engine.routing.RouteRequest;
import com.routeforge.engine.routing.RouteResult;
import com.routeforge.engine.routing.Router;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny command-line front end for the engine.
 *
 * <pre>
 *   plan --pbf data/liechtenstein-latest.osm.pbf \
 *        --from 47.142,9.524 --to 47.166,9.510 \
 *        --profile car --algo astar
 * </pre>
 *
 * Prints a JSON object with: algorithm, profile, snapped source/target coords,
 * distance, duration, elapsed time, nodes settled, and the polyline geometry.
 */
public final class RouteCli {

    public static void main(String[] args) throws IOException {
        Map<String, String> opts = parseArgs(args);
        Path pbf = Path.of(required(opts, "--pbf"));
        double[] from = parseLatLon(required(opts, "--from"));
        double[] to   = parseLatLon(required(opts, "--to"));
        String profile = opts.getOrDefault("--profile", "car");
        String algo    = opts.getOrDefault("--algo",    "astar");

        // Read graph.
        var graph = new OsmGraphReader().read(pbf);

        // Route.
        var router = new Router(graph);
        var req = new RouteRequest(from[0], from[1], to[0], to[1], profile, algo);
        long startNs = System.nanoTime();
        RouteResult res = router.route(req);
        long totalMs = (System.nanoTime() - startNs) / 1_000_000;

        System.out.println(toJson(res, req, graph.nodeCount(), graph.edgeCount(), totalMs));
    }

    // ---------- Arg parsing ----------

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].startsWith("--")) {
                m.put(args[i], args[i + 1]);
                i++;
            }
        }
        return m;
    }

    private static String required(Map<String, String> opts, String key) {
        String v = opts.get(key);
        if (v == null) {
            System.err.println("Missing required arg: " + key);
            System.err.println(USAGE);
            System.exit(64); // EX_USAGE
        }
        return v;
    }

    private static double[] parseLatLon(String s) {
        String[] parts = s.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected 'lat,lon', got: " + s);
        }
        return new double[]{ Double.parseDouble(parts[0].trim()),
                             Double.parseDouble(parts[1].trim()) };
    }

    private static final String USAGE = """
            Usage:
              plan --pbf <file.osm.pbf> \
                   --from <lat,lon> --to <lat,lon> \
                   [--profile car|bike|foot] \
                   [--algo dijkstra|astar|bidirectional]
            """;

    // ---------- JSON output (no library needed) ----------

    private static String toJson(RouteResult r, RouteRequest req,
                                 int nodes, int edges, long totalMs) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{\n");
        sb.append("  \"found\": ").append(r.found()).append(",\n");
        sb.append("  \"algorithm\": \"").append(r.algorithm()).append("\",\n");
        sb.append("  \"profile\": \"").append(req.profileName()).append("\",\n");
        sb.append("  \"graph\": { \"nodes\": ").append(nodes)
                .append(", \"edges\": ").append(edges).append(" },\n");
        sb.append("  \"distance_meters\": ").append(round(r.distanceMeters(), 1)).append(",\n");
        sb.append("  \"duration_seconds\": ").append(round(r.durationSeconds(), 1)).append(",\n");
        sb.append("  \"elapsed_ms\": ").append(r.elapsedMillis()).append(",\n");
        sb.append("  \"total_elapsed_ms\": ").append(totalMs).append(",\n");
        sb.append("  \"nodes_settled\": ").append(r.nodesSettled()).append(",\n");
        sb.append("  \"path_node_count\": ").append(r.nodePath().size()).append(",\n");
        sb.append("  \"geometry\": ");
        appendGeometry(sb, r.geometry());
        sb.append("\n}\n");
        return sb.toString();
    }

    private static void appendGeometry(StringBuilder sb, List<double[]> geom) {
        sb.append("[");
        for (int i = 0; i < geom.size(); i++) {
            if (i > 0) sb.append(", ");
            double[] p = geom.get(i);
            sb.append("[").append(round(p[0], 7))
              .append(", ").append(round(p[1], 7)).append("]");
        }
        sb.append("]");
    }

    private static double round(double v, int decimals) {
        double m = Math.pow(10, decimals);
        return Math.round(v * m) / m;
    }
}
