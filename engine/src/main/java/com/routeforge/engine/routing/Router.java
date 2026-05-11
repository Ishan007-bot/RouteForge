package com.routeforge.engine.routing;

import com.routeforge.engine.algo.AStar;
import com.routeforge.engine.algo.BidirectionalDijkstra;
import com.routeforge.engine.algo.Dijkstra;
import com.routeforge.engine.algo.ShortestPath;
import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.profile.BikeProfile;
import com.routeforge.engine.profile.CarProfile;
import com.routeforge.engine.profile.FootProfile;
import com.routeforge.engine.profile.Profile;

import java.util.Locale;
import java.util.Map;

/**
 * Orchestrator: resolves profile/algorithm names from a {@link RouteRequest},
 * snaps lat/lon to graph nodes, and runs the chosen {@link ShortestPath}.
 * <p>
 * One {@code Router} is bound to one {@link RoadGraph}; build a fresh one
 * after reloading the graph.
 */
public final class Router {

    private static final Map<String, Profile> PROFILES = Map.of(
            "car",  new CarProfile(),
            "bike", new BikeProfile(),
            "foot", new FootProfile()
    );

    private static final Map<String, ShortestPath> ALGORITHMS = Map.of(
            "dijkstra",      new Dijkstra(),
            "astar",         new AStar(),
            "bidirectional", new BidirectionalDijkstra()
    );

    private final RoadGraph graph;

    public Router(RoadGraph graph) {
        this.graph = graph;
    }

    public RouteResult route(RouteRequest req) {
        Profile profile = PROFILES.get(req.profileName().toLowerCase(Locale.ROOT));
        if (profile == null) {
            throw new IllegalArgumentException("Unknown profile: " + req.profileName()
                    + " (known: " + PROFILES.keySet() + ")");
        }
        ShortestPath algo = ALGORITHMS.get(req.algorithmName().toLowerCase(Locale.ROOT));
        if (algo == null) {
            throw new IllegalArgumentException("Unknown algorithm: " + req.algorithmName()
                    + " (known: " + ALGORITHMS.keySet() + ")");
        }

        int source = graph.nearestNode(req.fromLat(), req.fromLon());
        int target = graph.nearestNode(req.toLat(),   req.toLon());
        if (source == -1 || target == -1) {
            throw new IllegalStateException("Empty graph — load an OSM file first");
        }
        return algo.shortestPath(graph, source, target, profile);
    }

    public RoadGraph graph() { return graph; }
}
