package com.routeforge.api.dto;

import java.util.List;

/**
 * YAML-loaded simulation scenario.
 *
 * <pre>
 * name: "morning rush"
 * vehicles:
 *   - { profile: car,  fromLat: 47.16, fromLon: 9.51, toLat: 47.14, toLon: 9.53, count: 30 }
 *   - { profile: car,  fromLat: 47.14, fromLon: 9.53, toLat: 47.16, toLon: 9.51, count: 30 }
 * events:
 *   - { atSeconds: 30, type: close, lat: 47.150, lon: 9.520 }
 *   - { atSeconds: 90, type: open,  lat: 47.150, lon: 9.520 }
 * </pre>
 */
public record SimScenarioDto(
        String name,
        List<VehicleSpec> vehicles,
        List<EventSpec> events
) {
    public record VehicleSpec(
            String profile,
            double fromLat, double fromLon,
            double toLat,   double toLon,
            Integer count
    ) { }

    public record EventSpec(
            double atSeconds,
            String type,
            double lat,
            double lon
    ) { }
}
