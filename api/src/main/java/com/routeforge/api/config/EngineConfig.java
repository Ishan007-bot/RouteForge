package com.routeforge.api.config;

import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.osm.OsmGraphReader;
import com.routeforge.engine.routing.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Wires the engine into the Spring context.
 * <p>
 * On startup we load the OSM PBF once and expose the resulting graph + router
 * as singleton beans. Any controller / service that depends on them is
 * dependency-injected automatically.
 *
 * <h3>Why this is a {@code @Configuration}, not part of the application class</h3>
 * Keeping bean wiring in a separate class makes it easy to disable in tests:
 * the test profile uses {@link TestEngineConfig} to supply a tiny in-memory graph
 * instead of reading a PBF.
 */
@Configuration
@Profile("!test")
public class EngineConfig {

    private static final Logger log = LoggerFactory.getLogger(EngineConfig.class);

    @Bean
    public RoadGraph roadGraph(@Value("${routeforge.pbf-file:}") String pbfPath) throws IOException {
        if (pbfPath == null || pbfPath.isBlank()) {
            throw new IllegalStateException(
                    "routeforge.pbf-file is not configured. " +
                    "Set it via --routeforge.pbf-file=<path> or ROUTEFORGE_PBF_FILE=<path>.");
        }
        Path pbf = Path.of(pbfPath);
        if (!Files.isRegularFile(pbf)) {
            throw new IllegalStateException("PBF file not found: " + pbf.toAbsolutePath());
        }
        log.info("Loading OSM PBF: {}", pbf.toAbsolutePath());
        long startNs = System.nanoTime();
        RoadGraph graph = new OsmGraphReader().read(pbf);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("Engine ready in {} ms: {} nodes, {} edges",
                elapsedMs, graph.nodeCount(), graph.edgeCount());
        return graph;
    }

    @Bean
    public Router router(RoadGraph graph) {
        return new Router(graph);
    }
}
