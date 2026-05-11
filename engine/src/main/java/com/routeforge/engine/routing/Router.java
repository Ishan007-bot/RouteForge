package com.routeforge.engine.routing;

import com.routeforge.engine.algo.AStar;
import com.routeforge.engine.algo.BidirectionalDijkstra;
import com.routeforge.engine.algo.Dijkstra;
import com.routeforge.engine.algo.ShortestPath;
import com.routeforge.engine.ch.CHGraph;
import com.routeforge.engine.ch.CHPreprocessor;
import com.routeforge.engine.ch.CHQuery;
import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.profile.BikeProfile;
import com.routeforge.engine.profile.CarProfile;
import com.routeforge.engine.profile.FootProfile;
import com.routeforge.engine.profile.Profile;

import java.util.HashMap;
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

    private static final Map<String, ShortestPath> BASIC_ALGOS = Map.of(
            "dijkstra",      new Dijkstra(),
            "astar",         new AStar(),
            "bidirectional", new BidirectionalDijkstra()
    );

    private final RoadGraph graph;
    // Per-profile CH cache. Built on first "ch" query for that profile.
    private final Map<String, CHQuery> chByProfile = new HashMap<>();

    public Router(RoadGraph graph) {
        this.graph = graph;
    }

    public RouteResult route(RouteRequest req) {
        String profileName = req.profileName().toLowerCase(Locale.ROOT);
        Profile profile = PROFILES.get(profileName);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown profile: " + req.profileName()
                    + " (known: " + PROFILES.keySet() + ")");
        }
        String algoName = req.algorithmName().toLowerCase(Locale.ROOT);

        int source = graph.nearestNode(req.fromLat(), req.fromLon());
        int target = graph.nearestNode(req.toLat(),   req.toLon());
        if (source == -1 || target == -1) {
            throw new IllegalStateException("Empty graph — load an OSM file first");
        }

        ShortestPath algo;
        if ("ch".equals(algoName)) {
            algo = chByProfile.computeIfAbsent(profileName, p ->
                    new CHQuery(new CHPreprocessor(graph, profile).preprocess()));
        } else {
            algo = BASIC_ALGOS.get(algoName);
            if (algo == null) {
                throw new IllegalArgumentException("Unknown algorithm: " + req.algorithmName()
                        + " (known: dijkstra, astar, bidirectional, ch)");
            }
        }
        return algo.shortestPath(graph, source, target, profile);
    }

    /** Eagerly build and cache a CH for the named profile. */
    public CHGraph prepareCH(String profileName) {
        Profile p = PROFILES.get(profileName.toLowerCase(Locale.ROOT));
        if (p == null) throw new IllegalArgumentException("Unknown profile: " + profileName);
        CHQuery q = chByProfile.computeIfAbsent(profileName.toLowerCase(Locale.ROOT), pn ->
                new CHQuery(new CHPreprocessor(graph, p).preprocess()));
        return q.chGraph();
    }

    public RoadGraph graph() { return graph; }
}
