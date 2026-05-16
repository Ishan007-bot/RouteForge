//! Synthetic grid road graph generator.
//!
//! Generates an `n × n` lattice of nodes spaced ~`step_meters` apart in
//! lat/lon, with a directed edge between every cardinal-neighbour pair
//! (4-connected, both directions for two-way streets).
//!
//! Why a grid? Phase 6a needs a benchmark graph without an OSM PBF
//! loader. A grid is:
//!   * **Predictable** — n² nodes, ~4 n² edges. Knowable RAM cost.
//!   * **Adversarial enough** for Dijkstra — corner-to-corner queries
//!     touch most of the graph, so this stress-tests the heap and CSR
//!     traversal at scale.
//!   * **Comparable** — the Java engine can build the same shape with
//!     `RoadGraphBuilder`, giving a Java-vs-Rust apples-to-apples bench.

use routeforge_core::graph::{HighwayClass, RoadGraph, RoadGraphBuilder};
use routeforge_core::haversine::distance_meters;

/// Build an `n × n` grid centered on (`lat0`, `lon0`).
///
/// `step_meters` is the nominal spacing — we convert it to a latitude
/// degree-step at this point on the sphere. The actual edge lengths are
/// computed with Haversine for correctness, but stay close to
/// `step_meters` for small grids.
pub fn build(n: u32, lat0: f64, lon0: f64, step_meters: f64) -> RoadGraph {
    let n = n.max(2);

    // 1 degree of latitude ≈ 111_320 m everywhere. Longitude shrinks
    // with cos(lat). Compute both so the grid stays roughly square.
    let dlat = step_meters / 111_320.0;
    let dlon = step_meters / (111_320.0 * lat0.to_radians().cos().abs().max(1e-9));

    let nodes = (n as usize) * (n as usize);
    let edges = 4 * nodes;       // upper bound (interior nodes have 4)
    let mut b = RoadGraphBuilder::with_capacity(nodes, edges);

    // Pass 1: add nodes in row-major order so id = y * n + x.
    for y in 0..n {
        let lat = lat0 + (y as f64) * dlat;
        for x in 0..n {
            let lon = lon0 + (x as f64) * dlon;
            b.add_node(lat, lon);
        }
    }

    // Pass 2: connect cardinal neighbours. Add both directions for
    // each pair so the graph is two-way.
    let idx = |x: u32, y: u32| -> u32 { y * n + x };
    for y in 0..n {
        for x in 0..n {
            let here = idx(x, y);
            let (h_lat, h_lon) = node_pos(lat0, lon0, dlat, dlon, x, y);
            if x + 1 < n {
                let nb = idx(x + 1, y);
                let (b_lat, b_lon) = node_pos(lat0, lon0, dlat, dlon, x + 1, y);
                let len = distance_meters(h_lat, h_lon, b_lat, b_lon) as f32;
                b.add_edge(here, nb, len, HighwayClass::Residential, 30);
                b.add_edge(nb, here, len, HighwayClass::Residential, 30);
            }
            if y + 1 < n {
                let nb = idx(x, y + 1);
                let (b_lat, b_lon) = node_pos(lat0, lon0, dlat, dlon, x, y + 1);
                let len = distance_meters(h_lat, h_lon, b_lat, b_lon) as f32;
                b.add_edge(here, nb, len, HighwayClass::Residential, 30);
                b.add_edge(nb, here, len, HighwayClass::Residential, 30);
            }
        }
    }

    b.build().expect("grid builder always produces a valid graph")
}

#[inline]
fn node_pos(lat0: f64, lon0: f64, dlat: f64, dlon: f64, x: u32, y: u32) -> (f64, f64) {
    (lat0 + (y as f64) * dlat, lon0 + (x as f64) * dlon)
}
