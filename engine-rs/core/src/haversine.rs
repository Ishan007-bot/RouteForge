//! Great-circle distance on the sphere.
//!
//! Identical formula to the Java [`Haversine`] helper — kept in `f64`
//! throughout because the inputs are degrees that round-trip through
//! `sin`/`cos`, and `f32` rounding can introduce meter-scale error
//! over a one-kilometer edge.

const EARTH_RADIUS_METERS: f64 = 6_371_000.0;

#[inline]
pub fn distance_meters(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
    let phi1 = lat1.to_radians();
    let phi2 = lat2.to_radians();
    let dphi = (lat2 - lat1).to_radians();
    let dlam = (lon2 - lon1).to_radians();

    let a = (dphi * 0.5).sin().powi(2)
          + phi1.cos() * phi2.cos() * (dlam * 0.5).sin().powi(2);
    let c = 2.0 * a.sqrt().asin();
    EARTH_RADIUS_METERS * c
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn zero_distance_when_points_match() {
        assert_eq!(distance_meters(47.15, 9.52, 47.15, 9.52), 0.0);
    }

    #[test]
    fn matches_known_distance() {
        // London (51.5074, -0.1278) -> Paris (48.8566, 2.3522) ~ 343 km.
        let d = distance_meters(51.5074, -0.1278, 48.8566, 2.3522);
        assert!((d - 343_500.0).abs() < 2_000.0, "got {d}");
    }
}
