//! Travel profiles. Mirrors the Java `Profile` interface.
//!
//! A profile decides two things about every edge:
//! 1. **Allowed?** — cars can't use a footway, etc.
//! 2. **Cost** — typically duration in seconds; the algorithms minimise
//!    whatever this returns.
//!
//! The algorithms know nothing about roads — swap the profile, change
//! what "shortest" means.

use crate::graph::{HighwayClass, RoadGraph};

pub trait Profile {
    fn name(&self) -> &'static str;
    fn allowed(&self, graph: &RoadGraph, edge: u32) -> bool;
    fn cost(&self, graph: &RoadGraph, edge: u32) -> f64;
    /// Maximum physically plausible speed in m/s. Used by A* as the
    /// admissible-heuristic denominator. Underestimating is safe.
    fn max_speed_meters_per_second(&self) -> f64;
}

#[inline]
fn kmh_to_mps(kmh: f64) -> f64 { kmh * 1000.0 / 3600.0 }

/// Helper: edge duration in seconds for the given speed in km/h.
#[inline]
fn duration_secs(graph: &RoadGraph, edge: u32, speed_kmh: f64) -> f64 {
    let mps = kmh_to_mps(speed_kmh);
    graph.length_meters(edge) / mps
}

/* -------------------- Car -------------------- */

pub struct CarProfile;

impl Profile for CarProfile {
    fn name(&self) -> &'static str { "car" }

    fn allowed(&self, graph: &RoadGraph, edge: u32) -> bool {
        !matches!(
            graph.highway_class(edge),
            HighwayClass::Pedestrian
            | HighwayClass::Cycleway
            | HighwayClass::Footway
            | HighwayClass::Path
        )
    }

    fn cost(&self, graph: &RoadGraph, edge: u32) -> f64 {
        if !self.allowed(graph, edge) { return f64::INFINITY; }
        let kmh = graph.max_speed_kmh(edge) as f64;
        duration_secs(graph, edge, kmh.max(1.0))
    }

    fn max_speed_meters_per_second(&self) -> f64 { kmh_to_mps(130.0) }
}

/* -------------------- Bike -------------------- */

pub struct BikeProfile;

impl Profile for BikeProfile {
    fn name(&self) -> &'static str { "bike" }

    fn allowed(&self, graph: &RoadGraph, edge: u32) -> bool {
        !matches!(
            graph.highway_class(edge),
            HighwayClass::Motorway
            | HighwayClass::Trunk
            | HighwayClass::Pedestrian
            | HighwayClass::Footway
        )
    }

    fn cost(&self, graph: &RoadGraph, edge: u32) -> f64 {
        if !self.allowed(graph, edge) { return f64::INFINITY; }
        // Bikes ignore road speed limits and cruise at ~18 km/h.
        duration_secs(graph, edge, 18.0)
    }

    fn max_speed_meters_per_second(&self) -> f64 { kmh_to_mps(30.0) }
}

/* -------------------- Foot -------------------- */

pub struct FootProfile;

impl Profile for FootProfile {
    fn name(&self) -> &'static str { "foot" }

    fn allowed(&self, graph: &RoadGraph, edge: u32) -> bool {
        !matches!(
            graph.highway_class(edge),
            HighwayClass::Motorway | HighwayClass::Trunk,
        )
    }

    fn cost(&self, graph: &RoadGraph, edge: u32) -> f64 {
        if !self.allowed(graph, edge) { return f64::INFINITY; }
        duration_secs(graph, edge, 5.0)
    }

    fn max_speed_meters_per_second(&self) -> f64 { kmh_to_mps(8.0) }
}

/* -------------------- Resolver -------------------- */

/// Look up a profile by name. Returns `None` for unknowns so the
/// caller can pick the right error type.
pub fn by_name(name: &str) -> Option<Box<dyn Profile + Send + Sync>> {
    match name {
        "car"  => Some(Box::new(CarProfile)),
        "bike" => Some(Box::new(BikeProfile)),
        "foot" => Some(Box::new(FootProfile)),
        _ => None,
    }
}
