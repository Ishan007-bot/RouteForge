package com.routeforge.api.service;

import com.routeforge.api.dto.IsochroneResponseDto;
import com.routeforge.api.dto.RouteRequestDto;
import com.routeforge.api.dto.RouteResponseDto;
import com.routeforge.engine.algo.Isochrone;
import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.profile.BikeProfile;
import com.routeforge.engine.profile.CarProfile;
import com.routeforge.engine.profile.FootProfile;
import com.routeforge.engine.profile.Profile;
import com.routeforge.engine.routing.RouteResult;
import com.routeforge.engine.routing.Router;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Glue between controllers and the engine.
 * <p>
 * Why a service layer instead of calling the {@link Router} directly from
 * controllers? Three reasons:
 * <ol>
 *   <li><b>Caching annotations live here.</b> Spring Cache wraps service-bean
 *       methods, not controller methods.</li>
 *   <li><b>DTO translation.</b> Keeps controllers thin and focused on HTTP.</li>
 *   <li><b>Reusable from non-HTTP entry points</b> (CLI, scheduled jobs,
 *       future gRPC). Phase 5's simulator service will reuse this same bean.</li>
 * </ol>
 */
@Service
public class RouteService {

    private static final Map<String, Profile> PROFILES = Map.of(
            "car",  new CarProfile(),
            "bike", new BikeProfile(),
            "foot", new FootProfile()
    );

    private final Router router;
    private final RoadGraph graph;

    public RouteService(Router router, RoadGraph graph) {
        this.router = router;
        this.graph = graph;
    }

    /**
     * Plan a route. Result is cached by the full request — repeated identical
     * requests are served from memory in microseconds.
     */
    @Cacheable("routes")
    public RouteResponseDto route(RouteRequestDto req) {
        RouteResult res = router.route(req.toEngineRequest());
        String profile = req.profile() == null || req.profile().isBlank() ? "car" : req.profile();
        return RouteResponseDto.from(res, profile);
    }

    /** Compute the area reachable from a point within the given time budget. */
    public IsochroneResponseDto isochrone(double lat, double lon, double budgetSeconds, String profileName) {
        Profile profile = PROFILES.get((profileName == null ? "car" : profileName).toLowerCase(Locale.ROOT));
        if (profile == null) {
            throw new IllegalArgumentException("Unknown profile: " + profileName
                    + " (known: " + PROFILES.keySet() + ")");
        }
        int source = graph.nearestNode(lat, lon);
        if (source < 0) {
            throw new IllegalStateException("Empty graph — load an OSM file first");
        }
        Isochrone.Result r = new Isochrone().compute(graph, source, budgetSeconds, profile);

        // Compact representation: [lat, lon, secondsFromOrigin] per reachable node.
        List<double[]> pts = new ArrayList<>(r.nodes().size());
        for (var n : r.nodes()) {
            pts.add(new double[]{ graph.lat(n.nodeIndex()), graph.lon(n.nodeIndex()), n.costSeconds() });
        }
        return new IsochroneResponseDto(profile.name(), budgetSeconds,
                r.elapsedMillis(), r.nodes().size(), pts);
    }

    public RoadGraph graph() { return graph; }
}
