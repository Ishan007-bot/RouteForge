package com.routeforge.engine.geom;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HaversineTest {

    @Test
    void sameLocation_isZero() {
        assertThat(Haversine.distanceMeters(48.0, 11.0, 48.0, 11.0)).isEqualTo(0.0);
    }

    @Test
    void berlinToMunich_approxKnownDistance() {
        // Berlin TV tower ↔ Munich Marienplatz ≈ 504 km in a straight line.
        double m = Haversine.distanceMeters(52.5208, 13.4094, 48.1374, 11.5755);
        assertThat(m / 1000.0).isBetween(500.0, 510.0);
    }

    @Test
    void symmetry() {
        double a = Haversine.distanceMeters(40.0, -3.7, 51.5, -0.1);
        double b = Haversine.distanceMeters(51.5, -0.1, 40.0, -3.7);
        assertThat(a).isEqualTo(b);
    }
}
