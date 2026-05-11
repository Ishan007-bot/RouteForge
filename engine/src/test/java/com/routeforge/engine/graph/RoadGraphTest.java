package com.routeforge.engine.graph;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoadGraphTest {

    @Test
    void builderProducesCsrWithCorrectOffsetsAndTargets() {
        var b = new RoadGraphBuilder();
        int a = b.addNode(0, 0);
        int c = b.addNode(0.001, 0);
        int d = b.addNode(0, 0.001);
        // Add edges out of order to exercise the build-time sort.
        b.addEdge(c, d, 100, HighwayClass.RESIDENTIAL, 30);
        b.addEdge(a, c, 50,  HighwayClass.SECONDARY,   50);
        b.addEdge(a, d, 70,  HighwayClass.PRIMARY,     0);

        RoadGraph g = b.build();

        assertThat(g.nodeCount()).isEqualTo(3);
        assertThat(g.edgeCount()).isEqualTo(3);

        // Node a (=0) should have 2 outgoing edges; c (=1) has 1; d (=2) has 0.
        assertThat(g.endEdge(a) - g.firstEdge(a)).isEqualTo(2);
        assertThat(g.endEdge(c) - g.firstEdge(c)).isEqualTo(1);
        assertThat(g.endEdge(d) - g.firstEdge(d)).isEqualTo(0);

        // Verify edge classes round-trip.
        for (int e = g.firstEdge(a); e < g.endEdge(a); e++) {
            HighwayClass hc = g.highwayClass(e);
            assertThat(hc).isIn(HighwayClass.SECONDARY, HighwayClass.PRIMARY);
        }
    }

    @Test
    void reverseAdjacency_findsAllPredecessors() {
        var b = new RoadGraphBuilder();
        int a = b.addNode(0, 0);
        int c = b.addNode(0.001, 0);
        int d = b.addNode(0, 0.001);
        b.addEdge(a, c, 1, HighwayClass.RESIDENTIAL, 0);
        b.addEdge(d, c, 1, HighwayClass.RESIDENTIAL, 0);

        RoadGraph g = b.build();
        int inA = g.endInEdge(a) - g.firstInEdge(a);
        int inC = g.endInEdge(c) - g.firstInEdge(c);
        int inD = g.endInEdge(d) - g.firstInEdge(d);
        assertThat(inA).isZero();
        assertThat(inC).isEqualTo(2);
        assertThat(inD).isZero();
    }

    @Test
    void nearestNode_returnsClosest() {
        var b = new RoadGraphBuilder();
        b.addNode(48.0, 11.0);
        int target = b.addNode(48.1, 11.1);
        b.addNode(50.0, 12.0);
        RoadGraph g = b.build();

        assertThat(g.nearestNode(48.099, 11.099)).isEqualTo(target);
    }

    @Test
    void emptyGraph_nearestReturnsMinusOne() {
        var b = new RoadGraphBuilder();
        RoadGraph g = b.build();
        assertThat(g.nearestNode(0, 0)).isEqualTo(-1);
    }
}
