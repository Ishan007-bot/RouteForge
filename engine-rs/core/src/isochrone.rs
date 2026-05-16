//! Isochrone: every node reachable from `source` within `budget`.
//!
//! Same Dijkstra outward-search as `dijkstra::shortest_path`, but
//! instead of stopping at a target it stops when the popped key would
//! exceed the budget. Returns `(node, cost)` pairs for everything
//! settled under the cap.

use std::time::Instant;

use crate::graph::RoadGraph;
use crate::heap::IndexedBinaryHeap;
use crate::profile::Profile;

#[derive(Debug, Clone)]
pub struct IsochroneResult {
    /// `(node_index, cost_seconds)` for each reached node.
    pub points: Vec<(u32, f64)>,
    pub source: u32,
    pub budget_seconds: f64,
    pub elapsed_micros: u128,
    pub nodes_settled: u64,
}

pub fn compute(
    graph: &RoadGraph,
    source: u32,
    budget_seconds: f64,
    profile: &dyn Profile,
) -> IsochroneResult {
    let start = Instant::now();
    let n = graph.node_count();
    if n == 0 || (source as usize) >= n {
        return IsochroneResult {
            points: Vec::new(), source, budget_seconds,
            elapsed_micros: start.elapsed().as_micros(), nodes_settled: 0,
        };
    }

    let mut heap = IndexedBinaryHeap::with_capacity(n);
    heap.push_or_decrease(source, 0.0);

    let mut points: Vec<(u32, f64)> = Vec::new();
    let mut settled = 0u64;

    while let Some(u) = heap.pop_min() {
        let du = heap.key_of(u);
        if du > budget_seconds { break; }
        settled += 1;
        points.push((u, du));

        for e in graph.out_edges(u) {
            let c = profile.cost(graph, e);
            if !c.is_finite() { continue; }
            let alt = du + c;
            if alt > budget_seconds { continue; }
            let v = graph.target(e);
            if alt < heap.key_of(v) {
                heap.push_or_decrease(v, alt);
            }
        }
    }

    IsochroneResult {
        points, source, budget_seconds,
        elapsed_micros: start.elapsed().as_micros(),
        nodes_settled: settled,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::graph::{HighwayClass, RoadGraphBuilder};
    use crate::profile::CarProfile;

    #[test]
    fn reaches_more_nodes_with_a_bigger_budget() {
        // Chain: 0 - 100m - 1 - 100m - 2 - 100m - 3
        let mut b = RoadGraphBuilder::new();
        let n0 = b.add_node(0.0, 0.0);
        let n1 = b.add_node(0.0, 0.001);
        let n2 = b.add_node(0.0, 0.002);
        let n3 = b.add_node(0.0, 0.003);
        for (u, v) in [(n0, n1), (n1, n2), (n2, n3)] {
            b.add_edge(u, v, 100.0, HighwayClass::Residential, 30);
            b.add_edge(v, u, 100.0, HighwayClass::Residential, 30);
        }
        let g = b.build().unwrap();

        // 100m at 30km/h ≈ 12 s per hop.
        let small = compute(&g, n0, 6.0, &CarProfile);
        assert_eq!(small.points.len(), 1, "only the source fits in 6 s");

        let mid = compute(&g, n0, 14.0, &CarProfile);
        assert_eq!(mid.points.len(), 2, "source + first hop");

        let big = compute(&g, n0, 100.0, &CarProfile);
        assert_eq!(big.points.len(), 4);
    }
}
