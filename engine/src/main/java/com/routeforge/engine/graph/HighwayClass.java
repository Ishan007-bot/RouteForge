package com.routeforge.engine.graph;

/**
 * Categorical kind of OSM road, derived from the {@code highway=*} tag.
 * <p>
 * We collapse the full OSM tag taxonomy down to a small enum so each edge
 * can store its class in a single byte (the enum ordinal). Profiles use
 * this to decide:
 * <ul>
 *   <li>Whether the mode is allowed on this edge (e.g. pedestrians not on motorways).</li>
 *   <li>A default speed when the OSM way has no {@code maxspeed} tag.</li>
 * </ul>
 */
public enum HighwayClass {
    MOTORWAY      (130),
    TRUNK         (100),
    PRIMARY       ( 90),
    SECONDARY     ( 70),
    TERTIARY      ( 50),
    UNCLASSIFIED  ( 40),
    RESIDENTIAL   ( 30),
    LIVING_STREET ( 20),
    SERVICE       ( 20),
    PEDESTRIAN    (  5),
    TRACK         ( 15),
    FOOTWAY       (  5),
    CYCLEWAY      ( 15),
    PATH          (  5),
    STEPS         (  3),
    OTHER         ( 30);

    private final int defaultCarSpeedKmh;

    HighwayClass(int defaultCarSpeedKmh) {
        this.defaultCarSpeedKmh = defaultCarSpeedKmh;
    }

    /** Default car speed in km/h when no {@code maxspeed} tag is present. */
    public int defaultCarSpeedKmh() {
        return defaultCarSpeedKmh;
    }

    /**
     * Parse the value of an OSM {@code highway=*} tag.
     * Returns {@link #OTHER} for anything we don't recognize.
     */
    public static HighwayClass fromOsmTag(String tag) {
        if (tag == null) return OTHER;
        return switch (tag.toLowerCase()) {
            case "motorway", "motorway_link"           -> MOTORWAY;
            case "trunk", "trunk_link"                 -> TRUNK;
            case "primary", "primary_link"             -> PRIMARY;
            case "secondary", "secondary_link"         -> SECONDARY;
            case "tertiary", "tertiary_link"           -> TERTIARY;
            case "unclassified"                        -> UNCLASSIFIED;
            case "residential"                         -> RESIDENTIAL;
            case "living_street"                       -> LIVING_STREET;
            case "service"                             -> SERVICE;
            case "pedestrian"                          -> PEDESTRIAN;
            case "track"                               -> TRACK;
            case "footway"                             -> FOOTWAY;
            case "cycleway"                            -> CYCLEWAY;
            case "path"                                -> PATH;
            case "steps"                               -> STEPS;
            default                                    -> OTHER;
        };
    }
}
