package com.routeforge.api.config;

import com.routeforge.engine.graph.HighwayClass;
import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.graph.RoadGraphBuilder;
import com.routeforge.engine.routing.Router;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Test-profile alternative to {@link EngineConfig}.
 * <p>
 * Builds a tiny hand-crafted graph so the Spring context starts in
 * milliseconds without needing a real OSM PBF on disk. Used by:
 * <ul>
 *   <li>The context-loads smoke test ({@code @SpringBootTest(profiles="test")})</li>
 *   <li>Any integration test that wants a real router with predictable data</li>
 * </ul>
 */
@Configuration
@Profile("test")
public class TestEngineConfig {

    @Bean
    public RoadGraph roadGraph() {
        var b = new RoadGraphBuilder();
        // Triangle around (48, 11).
        int a = b.addNode(48.0,    11.0);
        int c = b.addNode(48.0010, 11.0);
        int d = b.addNode(48.0005, 11.0010);
        // All edges 100m, two-way.
        b.addEdge(a, c, 100, HighwayClass.RESIDENTIAL, 30);
        b.addEdge(c, a, 100, HighwayClass.RESIDENTIAL, 30);
        b.addEdge(c, d, 100, HighwayClass.RESIDENTIAL, 30);
        b.addEdge(d, c, 100, HighwayClass.RESIDENTIAL, 30);
        b.addEdge(a, d, 200, HighwayClass.RESIDENTIAL, 30); // long way
        b.addEdge(d, a, 200, HighwayClass.RESIDENTIAL, 30);
        return b.build();
    }

    @Bean
    public Router router(RoadGraph graph) {
        return new Router(graph);
    }
}
