package com.routeforge.engine.geom;

/**
 * Great-circle distance between two points on the Earth, in meters.
 * <p>
 * The Earth isn't a perfect sphere — but the haversine formula is plenty
 * accurate for routing (errors well under 0.5%). Used for:
 * <ul>
 *   <li>Edge lengths from OSM node coordinates.</li>
 *   <li>A*'s heuristic: a lower bound on the remaining distance to the goal.</li>
 * </ul>
 * The heuristic is <b>admissible</b> (never overestimates) because no path
 * along the road network can be shorter than the straight-line great-circle
 * distance — so A* with this heuristic always finds the true optimum.
 */
public final class Haversine {

    /** Mean Earth radius in meters (IUGG value). */
    public static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private Haversine() { /* utility class */ }

    /**
     * Great-circle distance between two points, in meters.
     *
     * @param lat1 latitude of point 1 in degrees
     * @param lon1 longitude of point 1 in degrees
     * @param lat2 latitude of point 2 in degrees
     * @param lon2 longitude of point 2 in degrees
     */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLam = Math.toRadians(lon2 - lon1);

        double sinHalfDPhi = Math.sin(dPhi / 2.0);
        double sinHalfDLam = Math.sin(dLam / 2.0);
        double a = sinHalfDPhi * sinHalfDPhi
                 + Math.cos(phi1) * Math.cos(phi2) * sinHalfDLam * sinHalfDLam;
        double c = 2.0 * Math.asin(Math.min(1.0, Math.sqrt(a)));
        return EARTH_RADIUS_METERS * c;
    }
}
