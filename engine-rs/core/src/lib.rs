//! RouteForge routing core (Rust port).
//!
//! Phase 6a: CSR graph, three profiles, indexed binary heap, Dijkstra.
//! Phase 6b: A★, Bidirectional Dijkstra, Contraction Hierarchies, Isochrone.

pub mod error;
pub mod graph;
pub mod haversine;
pub mod heap;
pub mod profile;
pub mod dijkstra;
pub mod astar;
pub mod bidir;
pub mod ch;
pub mod isochrone;

pub use error::{Result, RouteForgeError};
pub use graph::{HighwayClass, RoadGraph, RoadGraphBuilder};
pub use profile::{Profile, CarProfile, BikeProfile, FootProfile, by_name as profile_by_name};
pub use dijkstra::RouteResult;
pub use ch::CHGraph;
pub use isochrone::IsochroneResult;
