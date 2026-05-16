//! RouteForge routing core (Rust port).
//!
//! Phase 6a scope: CSR road graph, three travel profiles, indexed
//! binary heap, single-source Dijkstra. A*, bidirectional, and CH land
//! in phase 6b.

pub mod error;
pub mod graph;
pub mod haversine;
pub mod heap;
pub mod profile;
pub mod dijkstra;

pub use error::{Result, RouteForgeError};
pub use graph::{HighwayClass, RoadGraph, RoadGraphBuilder};
pub use profile::{Profile, CarProfile, BikeProfile, FootProfile, by_name as profile_by_name};
pub use dijkstra::{shortest_path, RouteResult};
