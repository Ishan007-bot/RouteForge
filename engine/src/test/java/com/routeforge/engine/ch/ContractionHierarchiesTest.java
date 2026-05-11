package com.routeforge.engine.ch;

import com.routeforge.engine.algo.Dijkstra;
import com.routeforge.engine.graph.HighwayClass;
import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.graph.RoadGraphBuilder;
import com.routeforge.engine.profile.CarProfile;
import com.routeforge.engine.profile.Profile;
import com.routeforge.engine.routing.RouteResult;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Correctness tests for the CH preprocessor + query.
 * <p>
 * The strategy is the same as for the other algorithms: hand-built graphs
 * where the right answer is obvious, plus a randomized stress test that
 * cross-checks CH against plain Dijkstra. <b>If CH ever disagrees with
 * Dijkstra on cost, CH is wrong.</b>
 */
class ContractionHierarchiesTest {

    private final Profile car = new CarProfile();

    @Test
    void singleEdge_chMatchesDijkstra() {
        var b = new RoadGraphBuilder();
        int a = b.addNode(48.0, 11.0);
        int c = b.addNode(48.001, 11.0);
        b.addEdge(a, c, 100, HighwayClass.RESIDENTIAL, 50);
        b.addEdge(c, a, 100, HighwayClass.RESIDENTIAL, 50);
        RoadGraph g = b.build();

        CHGraph ch = new CHPreprocessor(g, car).preprocess();
        var chq = new CHQuery(ch);

        RouteResult dij = new Dijkstra().shortestPath(g, a, c, car);
        RouteResult chr = chq.shortestPath(g, a, c, car);

        assertThat(chr.found()).isTrue();
        assertThat(chr.durationSeconds()).isCloseTo(dij.durationSeconds(), within(1e-9));
        assertThat(chr.distanceMeters()).isCloseTo(dij.distanceMeters(), within(1e-6));
        // Unpacked path must end at the right place.
        assertThat(chr.nodePath()).startsWith(a).endsWith(c);
    }

    @Test
    void diamondGraph_chMatchesDijkstra() {
        // Same diamond as AlgorithmsTest.
        var b = new RoadGraphBuilder();
        int n0 = b.addNode(48.0000, 11.0000);
        int n1 = b.addNode(48.0001, 11.0000);
        int n2 = b.addNode(48.0000, 11.0001);
        int n3 = b.addNode(48.0001, 11.0001);
        int n4 = b.addNode(48.0002, 11.0000);
        addBoth(b, n0, n1, 1000);
        addBoth(b, n1, n4, 1000);
        addBoth(b, n0, n2,  500);
        addBoth(b, n2, n4,  500);
        addBoth(b, n0, n3,  100);
        addBoth(b, n3, n4, 5000);

        RoadGraph g = b.build();
        CHGraph ch = new CHPreprocessor(g, car).preprocess();
        var chq = new CHQuery(ch);

        for (int s = 0; s < g.nodeCount(); s++) {
            for (int t = 0; t < g.nodeCount(); t++) {
                if (s == t) continue;
                RouteResult dij = new Dijkstra().shortestPath(g, s, t, car);
                RouteResult chr = chq.shortestPath(g, s, t, car);
                assertThat(chr.found()).as("found s=%d t=%d", s, t).isEqualTo(dij.found());
                if (dij.found()) {
                    assertThat(chr.durationSeconds())
                            .as("duration s=%d t=%d", s, t)
                            .isCloseTo(dij.durationSeconds(), within(1e-9));
                    assertThat(chr.nodePath().get(0)).isEqualTo(s);
                    assertThat(chr.nodePath().get(chr.nodePath().size() - 1)).isEqualTo(t);
                }
            }
        }
    }

    @Test
    void disconnectedComponents_unreachable() {
        var b = new RoadGraphBuilder();
        int a = b.addNode(0, 0);
        int c = b.addNode(0.001, 0);
        int isolated = b.addNode(1, 1);
        addBoth(b, a, c, 100);
        RoadGraph g = b.build();

        CHGraph ch = new CHPreprocessor(g, car).preprocess();
        var chq = new CHQuery(ch);
        assertThat(chq.shortestPath(g, a, isolated, car).found()).isFalse();
    }

    @Test
    void onewayRespected() {
        var b = new RoadGraphBuilder();
        int a = b.addNode(0, 0);
        int c = b.addNode(0.001, 0);
        b.addEdge(a, c, 100, HighwayClass.RESIDENTIAL, 50);  // only a→c
        RoadGraph g = b.build();

        CHGraph ch = new CHPreprocessor(g, car).preprocess();
        var chq = new CHQuery(ch);
        assertThat(chq.shortestPath(g, a, c, car).found()).isTrue();
        assertThat(chq.shortestPath(g, c, a, car).found()).isFalse();
    }

    /**
     * The killer test: random graph + many random queries. Every CH answer
     * must agree with Dijkstra on cost. If a CH bug exists this fails fast.
     */
    @Test
    void randomGraph_chAgreesWithDijkstra() {
        Random rng = new Random(7);
        int n = 120;
        var b = new RoadGraphBuilder();
        for (int i = 0; i < n; i++) {
            b.addNode(48.0 + rng.nextDouble() * 0.001, 11.0 + rng.nextDouble() * 0.001);
        }
        // Connectedness chain.
        for (int i = 0; i < n - 1; i++) addBoth(b, i, i + 1, 100 + rng.nextInt(900));
        // Random extra edges.
        for (int e = 0; e < n * 3; e++) {
            int u = rng.nextInt(n), v = rng.nextInt(n);
            if (u != v) addBoth(b, u, v, 200 + rng.nextInt(2000));
        }
        RoadGraph g = b.build();
        CHGraph ch = new CHPreprocessor(g, car).preprocess();
        var chq = new CHQuery(ch);
        Dijkstra dij = new Dijkstra();

        for (int trial = 0; trial < 40; trial++) {
            int s = rng.nextInt(n);
            int t = rng.nextInt(n);
            RouteResult ref = dij.shortestPath(g, s, t, car);
            RouteResult chr = chq.shortestPath(g, s, t, car);
            assertThat(chr.found())
                    .as("trial %d s=%d t=%d found", trial, s, t)
                    .isEqualTo(ref.found());
            if (ref.found()) {
                assertThat(chr.durationSeconds())
                        .as("trial %d s=%d t=%d duration", trial, s, t)
                        .isCloseTo(ref.durationSeconds(), within(1e-6));
            }
        }
    }

    private static void addBoth(RoadGraphBuilder b, int u, int v, double meters) {
        b.addEdge(u, v, meters, HighwayClass.RESIDENTIAL, 50);
        b.addEdge(v, u, meters, HighwayClass.RESIDENTIAL, 50);
    }
}
