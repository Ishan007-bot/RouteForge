package com.routeforge.engine.routing;

/**
 * A high-level routing query expressed in real-world units (lat/lon, profile name).
 * The router resolves these to graph node indices and runs the chosen algorithm.
 *
 * @param fromLat       latitude of the start point in degrees
 * @param fromLon       longitude of the start point in degrees
 * @param toLat         latitude of the destination in degrees
 * @param toLon         longitude of the destination in degrees
 * @param profileName   one of {@code "car"}, {@code "bike"}, {@code "foot"}
 * @param algorithmName one of {@code "dijkstra"}, {@code "astar"}, {@code "bidirectional"}
 */
public record RouteRequest(
        double fromLat, double fromLon,
        double toLat,   double toLon,
        String profileName,
        String algorithmName
) { }
