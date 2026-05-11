package com.routeforge.engine.algo;

import com.routeforge.engine.graph.HighwayClass;
import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.graph.RoadGraphBuilder;
import com.routeforge.engine.profile.CarProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IsochroneTest {

    @Test
    void emptyBudget_reachesOnlySource() {
        var b = new RoadGraphBuilder();
        int a = b.addNode(0, 0);
        int c = b.addNode(0.001, 0);
        b.addEdge(a, c, 100, HighwayClass.RESIDENTIAL, 30);
        RoadGraph g = b.build();

        var r = new Isochrone().compute(g, a, 0.0, new CarProfile());
        assertThat(r.nodes()).hasSize(1);
        assertThat(r.nodes().get(0).nodeIndex()).isEqualTo(a);
        assertThat(r.nodes().get(0).costSeconds()).isZero();
    }

    @Test
    void budgetCoversAll_reachesEverything() {
        var b = new RoadGraphBuilder();
        int a = b.addNode(0, 0);
        int c = b.addNode(0.001, 0);
        int d = b.addNode(0.002, 0);
        b.addEdge(a, c, 100, HighwayClass.RESIDENTIAL, 30);
        b.addEdge(c, d, 100, HighwayClass.RESIDENTIAL, 30);
        RoadGraph g = b.build();

        // Generous budget — should settle all three nodes.
        var r = new Isochrone().compute(g, a, 1_000_000.0, new CarProfile());
        assertThat(r.nodes()).extracting(Isochrone.ReachedNode::nodeIndex)
                .containsExactlyInAnyOrder(a, c, d);
    }

    @Test
    void disconnectedNodes_areExcluded() {
        var b = new RoadGraphBuilder();
        int a = b.addNode(0, 0);
        int c = b.addNode(0.001, 0);
        int isolated = b.addNode(1, 1);
        b.addEdge(a, c, 100, HighwayClass.RESIDENTIAL, 30);
        b.addEdge(c, a, 100, HighwayClass.RESIDENTIAL, 30);
        RoadGraph g = b.build();

        var r = new Isochrone().compute(g, a, 999_999.0, new CarProfile());
        assertThat(r.nodes()).extracting(Isochrone.ReachedNode::nodeIndex)
                .doesNotContain(isolated);
    }
}
