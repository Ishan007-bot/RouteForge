package com.routeforge.engine.algo;

import com.routeforge.engine.graph.HighwayClass;
import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.graph.RoadGraphBuilder;
import com.routeforge.engine.profile.CarProfile;
import com.routeforge.engine.profile.Profile;
import com.routeforge.engine.routing.RouteResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Correctness tests for the three shortest-path implementations.
 * <p>
 * Strategy:
 * <ul>
 *   <li>Build small hand-crafted graphs where the right answer is obvious.</li>
 *   <li>Run all three algorithms and assert they agree on
 *       (a) the total cost, (b) reachability.</li>
 *   <li>Stress test on a random graph: all three must report the same distance.</li>
 * </ul>
 *
 * Nodes are placed with negligible lat/lon spread so the haversine heuristic is
 * tiny relative to edge lengths — keeps A* admissibility trivially satisfied
 * and removes coordinate quirks from the test.
 */
class AlgorithmsTest {

    private final Profile car = new CarProfile();
    private final List<ShortestPath> algos = List.of(new Dijkstra(), new AStar(), new BidirectionalDijkstra());

    @Test
    void diamondGraph_allAlgorithmsAgree() {
        // 5 nodes in a "diamond":
        //   0 has two paths to 4: via 1 (long) or via 2 (short). Node 3 is a trap.
        var b = new RoadGraphBuilder();
        int n0 = b.addNode(48.0000, 11.0000);
        int n1 = b.addNode(48.0001, 11.0000);
        int n2 = b.addNode(48.0000, 11.0001);
        int n3 = b.addNode(48.0001, 11.0001);
        int n4 = b.addNode(48.0002, 11.0000);

        // Add both directions (non-oneway).
        addBoth(b, n0, n1, 1000); // long side
        addBoth(b, n1, n4, 1000);
        addBoth(b, n0, n2,  500); // short side
        addBoth(b, n2, n4,  500);
        addBoth(b, n0, n3,  100);
        addBoth(b, n3, n4, 5000); // trap

        RoadGraph g = b.build();

        Double commonDuration = null;
        for (ShortestPath algo : algos) {
            RouteResult r = algo.shortestPath(g, n0, n4, car);
            assertThat(r.found()).as("algo %s should find a path", algo.name()).isTrue();
            // Best path goes via n2 with total length 1000m.
            assertThat(r.distanceMeters()).as("algo %s distance", algo.name())
                    .isCloseTo(1000.0, within(1e-6));
            assertThat(r.nodePath()).as("algo %s path", algo.name())
                    .containsExactly(n0, n2, n4);
            if (commonDuration == null) commonDuration = r.durationSeconds();
            else assertThat(r.durationSeconds()).isCloseTo(commonDuration, within(1e-9));
        }
    }

    @Test
    void unreachable_target_isReportedAsNotFound() {
        // Two disconnected components.
        var b = new RoadGraphBuilder();
        int a = b.addNode(0, 0);
        int c = b.addNode(0.001, 0);
        int d = b.addNode(1, 1);
        addBoth(b, a, c, 100);
        // d is isolated.
        RoadGraph g = b.build();

        for (ShortestPath algo : algos) {
            RouteResult r = algo.shortestPath(g, a, d, car);
            assertThat(r.found()).as("algo %s on disconnected target", algo.name()).isFalse();
        }
    }

    @Test
    void sourceEqualsTarget_returnsTrivialPath() {
        var b = new RoadGraphBuilder();
        int a = b.addNode(0, 0);
        int c = b.addNode(0.001, 0);
        addBoth(b, a, c, 100);
        RoadGraph g = b.build();

        for (ShortestPath algo : algos) {
            RouteResult r = algo.shortestPath(g, a, a, car);
            assertThat(r.found()).as("algo %s self-route", algo.name()).isTrue();
            assertThat(r.distanceMeters()).isZero();
            assertThat(r.durationSeconds()).isZero();
            assertThat(r.nodePath()).containsExactly(a);
        }
    }

    @Test
    void onewayStreet_isHonored() {
        // a -> c only. Source=a target should reach c; from c target=a is unreachable.
        var b = new RoadGraphBuilder();
        int a = b.addNode(0, 0);
        int c = b.addNode(0.001, 0);
        b.addEdge(a, c, 100, HighwayClass.RESIDENTIAL, 30);
        RoadGraph g = b.build();

        for (ShortestPath algo : algos) {
            assertThat(algo.shortestPath(g, a, c, car).found())
                    .as("algo %s a→c", algo.name()).isTrue();
            assertThat(algo.shortestPath(g, c, a, car).found())
                    .as("algo %s c→a (wrong way on oneway)", algo.name()).isFalse();
        }
    }

    /**
     * Stress: generate a random connected graph, ask each algorithm for
     * shortest paths between random pairs. All three must agree on cost.
     */
    @Test
    void randomGraph_allAlgorithmsAgreeOnCost() {
        long seed = 12345;
        Random rng = new Random(seed);
        int n = 200;

        var b = new RoadGraphBuilder();
        // Nodes in a tight lat/lon cluster so haversine is tiny.
        for (int i = 0; i < n; i++) {
            b.addNode(48.0 + rng.nextDouble() * 0.001, 11.0 + rng.nextDouble() * 0.001);
        }
        // Random edges; ensure connectedness by also chaining 0→1→2→...→n-1.
        for (int i = 0; i < n - 1; i++) {
            addBoth(b, i, i + 1, 100 + rng.nextInt(900));
        }
        for (int e = 0; e < n * 4; e++) {
            int u = rng.nextInt(n);
            int v = rng.nextInt(n);
            if (u == v) continue;
            addBoth(b, u, v, 200 + rng.nextInt(2000));
        }
        RoadGraph g = b.build();

        for (int trial = 0; trial < 25; trial++) {
            int s = rng.nextInt(n);
            int t = rng.nextInt(n);
            RouteResult ref = algos.get(0).shortestPath(g, s, t, car);
            for (int i = 1; i < algos.size(); i++) {
                RouteResult r = algos.get(i).shortestPath(g, s, t, car);
                assertThat(r.found()).as("algo %s vs %s found", algos.get(i).name(), algos.get(0).name())
                        .isEqualTo(ref.found());
                if (ref.found()) {
                    // Same total cost (duration). Distance can differ only if there are
                    // ties — unlikely with random weights.
                    assertThat(r.durationSeconds())
                            .as("trial %d s=%d t=%d algo=%s", trial, s, t, algos.get(i).name())
                            .isCloseTo(ref.durationSeconds(), within(1e-6));
                }
            }
        }
    }

    private static void addBoth(RoadGraphBuilder b, int u, int v, double meters) {
        b.addEdge(u, v, meters, HighwayClass.RESIDENTIAL, 50);
        b.addEdge(v, u, meters, HighwayClass.RESIDENTIAL, 50);
    }
}
