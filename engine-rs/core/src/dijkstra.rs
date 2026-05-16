//! Single-source single-target Dijkstra over a [`RoadGraph`].
//!
//! Mirrors the Java implementation: indexed min-heap with O(log n)
//! decrease-key, parallel `dist`/`prev` arrays, early termination when
//! the target pops. Asymptotically `O((V + E) log V)`.

use std::time::Instant;

use crate::graph::RoadGraph;
use crate::heap::IndexedBinaryHeap;
use crate::profile::Profile;

/// Result of a routing query.
#[derive(Debug, Clone)]
pub struct RouteResult {
    pub found: bool,
    pub node_path: Vec<u32>,
    pub geometry: Vec<(f64, f64)>,   // (lat, lon) pairs
    pub distance_meters: f64,
    pub duration_seconds: f64,
    pub algorithm: &'static str,
    pub elapsed_micros: u128,
    pub nodes_settled: u64,
}

impl RouteResult {
    fn not_found(algorithm: &'static str, elapsed_micros: u128, nodes_settled: u64) -> Self {
        Self {
            found: false, node_path: Vec::new(), geometry: Vec::new(),
            distance_meters: 0.0, duration_seconds: 0.0,
            algorithm, elapsed_micros, nodes_settled,
        }
    }
}

/// Run Dijkstra from `source` to `target` under the given profile.
pub fn shortest_path(
    graph: &RoadGraph,
    source: u32,
    target: u32,
    profile: &dyn Profile,
) -> RouteResult {
    let start = Instant::now();
    let n = graph.node_count();
    if n == 0 || (source as usize) >= n || (target as usize) >= n {
        return RouteResult::not_found("dijkstra", start.elapsed().as_micros(), 0);
    }

    let mut prev: Vec<i32> = vec![-1; n];
    let mut heap = IndexedBinaryHeap::with_capacity(n);
    heap.push_or_decrease(source, 0.0);

    let mut settled = 0u64;

    while let Some(u) = heap.pop_min() {
        settled += 1;
        if u == target {
            return reconstruct(graph, profile, &prev, source, target, settled, start);
        }
        let du = heap.key_of(u);

        for e in graph.out_edges(u) {
            let c = profile.cost(graph, e);
            if !c.is_finite() { continue; }
            let v = graph.target(e);
            let alt = du + c;
            if alt < heap.key_of(v) {
                if heap.push_or_decrease(v, alt) {
                    prev[v as usize] = e as i32;
                }
            }
        }
    }

    RouteResult::not_found("dijkstra", start.elapsed().as_micros(), settled)
}

fn reconstruct(
    graph: &RoadGraph,
    profile: &dyn Profile,
    prev: &[i32],
    source: u32,
    target: u32,
    settled: u64,
    start: Instant,
) -> RouteResult {
    let mut node_path: Vec<u32> = Vec::new();
    let mut cur = target;
    loop {
        node_path.push(cur);
        if cur == source { break; }
        let e = prev[cur as usize];
        if e < 0 {
            // shouldn't happen if target was popped, but guard anyway
            return RouteResult::not_found("dijkstra", start.elapsed().as_micros(), settled);
        }
        // Recover the source of edge e by walking through outgoing
        // edges of every node. With only outgoing CSR this is O(n+m);
        // we'll add a stored `prev_node` array when we port bidir/CH.
        // For now use a small helper: the source of edge e is the
        // node whose first_edge <= e < end_edge — find it via binary
        // search on the prefix-sum array.
        cur = source_of_edge(graph, e as u32);
    }
    node_path.reverse();

    let mut distance_meters = 0.0;
    let mut duration_seconds = 0.0;
    for &n in node_path.iter().skip(1) {
        let e = prev[n as usize] as u32;
        distance_meters += graph.length_meters(e);
        duration_seconds += profile.cost(graph, e);
    }

    let geometry: Vec<(f64, f64)> =
        node_path.iter().map(|&n| (graph.lat(n), graph.lon(n))).collect();

    RouteResult {
        found: true,
        node_path,
        geometry,
        distance_meters,
        duration_seconds,
        algorithm: "dijkstra",
        elapsed_micros: start.elapsed().as_micros(),
        nodes_settled: settled,
    }
}

/// Find the source node of edge `e` by binary-searching the CSR
/// prefix-sum array. O(log n).
fn source_of_edge(graph: &RoadGraph, e: u32) -> u32 {
    let n = graph.node_count();
    // Find largest i such that first_edge(i) <= e.
    let (mut lo, mut hi) = (0i64, n as i64 - 1);
    while lo < hi {
        let mid = (lo + hi + 1) / 2;
        if graph.first_edge(mid as u32) <= e { lo = mid; } else { hi = mid - 1; }
    }
    lo as u32
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::graph::{HighwayClass, RoadGraphBuilder};
    use crate::profile::CarProfile;

    fn linear_chain() -> (crate::graph::RoadGraph, [u32; 4]) {
        // Four nodes in a line a -> b -> c -> d, each leg 100 m.
        let mut b = RoadGraphBuilder::new();
        let n0 = b.add_node(0.0, 0.0);
        let n1 = b.add_node(0.0, 0.001);
        let n2 = b.add_node(0.0, 0.002);
        let n3 = b.add_node(0.0, 0.003);
        b.add_edge(n0, n1, 100.0, HighwayClass::Residential, 30);
        b.add_edge(n1, n2, 100.0, HighwayClass::Residential, 30);
        b.add_edge(n2, n3, 100.0, HighwayClass::Residential, 30);
        (b.build().unwrap(), [n0, n1, n2, n3])
    }

    #[test]
    fn finds_a_simple_path() {
        let (g, ns) = linear_chain();
        let r = shortest_path(&g, ns[0], ns[3], &CarProfile);
        assert!(r.found);
        assert_eq!(r.node_path, vec![ns[0], ns[1], ns[2], ns[3]]);
        assert!((r.distance_meters - 300.0).abs() < 1e-6);
    }

    #[test]
    fn picks_the_shorter_route() {
        // Diamond: 0 -> 1 -> 3 (200m) vs 0 -> 2 -> 3 (50m).
        let mut b = RoadGraphBuilder::new();
        let n0 = b.add_node(0.0, 0.0);
        let n1 = b.add_node(0.0, 0.002);
        let n2 = b.add_node(0.0, 0.0005);
        let n3 = b.add_node(0.0, 0.001);
        b.add_edge(n0, n1, 100.0, HighwayClass::Residential, 30);
        b.add_edge(n1, n3, 100.0, HighwayClass::Residential, 30);
        b.add_edge(n0, n2, 25.0,  HighwayClass::Residential, 30);
        b.add_edge(n2, n3, 25.0,  HighwayClass::Residential, 30);
        let g = b.build().unwrap();
        let r = shortest_path(&g, n0, n3, &CarProfile);
        assert!(r.found);
        assert_eq!(r.node_path, vec![n0, n2, n3]);
        assert!((r.distance_meters - 50.0).abs() < 1e-6);
    }

    #[test]
    fn returns_not_found_when_disconnected() {
        let mut b = RoadGraphBuilder::new();
        let n0 = b.add_node(0.0, 0.0);
        let _  = b.add_node(0.0, 1.0);
        let _  = b.add_node(0.0, 1.0);
        let n3 = b.add_node(0.0, 1.0);
        // No edges at all.
        let g = b.build().unwrap();
        let r = shortest_path(&g, n0, n3, &CarProfile);
        assert!(!r.found);
    }
}
