package com.routeforge.api.config;

import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.profile.BikeProfile;
import com.routeforge.engine.profile.CarProfile;
import com.routeforge.engine.profile.FootProfile;
import com.routeforge.engine.profile.Profile;
import com.routeforge.engine.sim.SimulationEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Exposes a single {@link SimulationEngine} bean bound to the running
 * graph. Active under both the default and {@code test} profiles — the
 * sim is cheap to construct and adds no startup cost when unused.
 *
 * <p>The engine starts in a paused state; controllers call {@code start()}
 * via the {@code /api/sim/control} endpoint when the user hits play.
 */
@Configuration
public class SimulationConfig {

    @Bean
    public SimulationEngine simulationEngine(RoadGraph graph) {
        Map<String, Profile> profiles = Map.of(
                "car",  new CarProfile(),
                "bike", new BikeProfile(),
                "foot", new FootProfile()
        );
        return new SimulationEngine(graph, profiles);
    }
}
