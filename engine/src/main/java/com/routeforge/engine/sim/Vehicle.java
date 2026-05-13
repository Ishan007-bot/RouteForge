package com.routeforge.engine.sim;

/**
 * A vehicle in the simulation. Mutable, package-private accessors —
 * owned by {@link SimulationEngine} which mutates state on its tick
 * thread only.
 */
public final class Vehicle {

    public enum Status { ACTIVE, ARRIVED, STUCK }

    public final int id;
    public final String profileName;
    public final double destLat;
    public final double destLon;

    /** Edges along the remaining planned path. */
    int[] edges;
    /** Index of the current edge in {@link #edges}. */
    int edgeCursor;
    /** Distance traveled along the current edge, in meters. */
    double progressMeters;

    /** Cached presentation state. */
    double lat;
    double lon;
    double heading;

    Status status = Status.ACTIVE;
    double totalDistance;
    long reroutes;

    Vehicle(int id, String profileName, double destLat, double destLon) {
        this.id          = id;
        this.profileName = profileName;
        this.destLat     = destLat;
        this.destLon     = destLon;
    }
}
