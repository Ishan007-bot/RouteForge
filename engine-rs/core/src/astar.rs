//! A★ search with an admissible heuristic.
//!
//! Same `RouteResult` shape as [`crate::dijkstra::shortest_path`]; same
//! invariants (returns the optimal path). The only difference is the
//! heuristic: each node `v`'s priority is
//!
//! ```text
//!     f(v) = g(v) + h(v, target)
//! ```
//!
//! where `g` is the best known cost from source to `v` and `h` is a
//! lower bound on the remaining cost from `v` to target. Admissibility
//! (never overestimating) is what guarantees A★ returns the optimum;
//! the closer `h` gets to the true remaining cost, the fewer nodes the
//! search visits.
//!
//! Our `h` is great-circle distance divided by the profile's top
//! speed: physically the fastest you could finish from here, which is
//! always ≤ the actual time. Cars get more speedup than feet because
//! a car's heuristic is more "informative" relative to its costs.

use std::time::Instant;

use crate::dijkstra::RouteResult;
use crate::graph::RoadGraph;
use crate::haversine::distance_meters;
use crate::heap::IndexedBinaryHeap;
use crate::profile::Profile;

pub fn shortest_path(
    graph: &RoadGraph,
    source: u32,
    target: u32,
    profile: &dyn Profile,
) -> RouteResult {
    let start = Instant::now();
    let n = graph.node_count();
    if n == 0 || (source as usize) >= n || (target as usize) >= n {
        return RouteResult {
            found: false, node_path: Vec::new(), geometry: Vec::new(),
            distance_meters: 0.0, duration_seconds: 0.0,
            algorithm: "astar", elapsed_micros: start.elapsed().as_micros(),
            nodes_settled: 0,
        };
    }

    let max_speed = profile.max_speed_meters_per_second().max(1e-9);
    let tgt_lat = graph.lat(target);
    let tgt_lon = graph.lon(target);

    let h = |v: u32| -> f64 {
        distance_meters(graph.lat(v), graph.lon(v), tgt_lat, tgt_lon) / max_speed
    };

    let mut g_score: Vec<f64> = vec![f64::INFINITY; n];
    let mut prev:    Vec<i32> = vec![-1; n];
    let mut heap = IndexedBinaryHeap::with_capacity(n);

    g_score[source as usize] = 0.0;
    heap.push_or_decrease(source, h(source));

    let mut settled = 0u64;

    while let Some(u) = heap.pop_min() {
        settled += 1;
        if u == target {
            return reconstruct(graph, profile, &prev, &g_score, source, target, settled, start);
        }
        let g_u = g_score[u as usize];

        for e in graph.out_edges(u) {
            let c = profile.cost(graph, e);
            if !c.is_finite() { continue; }
            let v = graph.target(e);
            let tentative_g = g_u + c;
            if tentative_g < g_score[v as usize] {
                g_score[v as usize] = tentative_g;
                prev[v as usize] = e as i32;
                let f = tentative_g + h(v);
                heap.push_or_decrease(v, f);
            }
        }
    }

    RouteResult {
        found: false, node_path: Vec::new(), geometry: Vec::new(),
        distance_meters: 0.0, duration_seconds: 0.0,
        algorithm: "astar", elapsed_micros: start.elapsed().as_micros(),
        nodes_settled: settled,
    }
}

fn reconstruct(
    graph: &RoadGraph,
    profile: &dyn Profile,
    prev: &[i32],
    g_score: &[f64],
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
            return RouteResult {
                found: false, node_path: Vec::new(), geometry: Vec::new(),
                distance_meters: 0.0, duration_seconds: 0.0,
                algorithm: "astar", elapsed_micros: start.elapsed().as_micros(),
                nodes_settled: settled,
            };
        }
        cur = source_of_edge(graph, e as u32);
    }
    node_path.reverse();

    let mut distance_meters = 0.0;
    for &nd in node_path.iter().skip(1) {
        let e = prev[nd as usize] as u32;
        distance_meters += graph.length_meters(e);
    }
    let duration_seconds = g_score[target as usize];
    let _ = profile;  // duration comes straight from g_score

    let geometry: Vec<(f64, f64)> =
        node_path.iter().map(|&nd| (graph.lat(nd), graph.lon(nd))).collect();

    RouteResult {
        found: true,
        node_path,
        geometry,
        distance_meters,
        duration_seconds,
        algorithm: "astar",
        elapsed_micros: start.elapsed().as_micros(),
        nodes_settled: settled,
    }
}

fn source_of_edge(graph: &RoadGraph, e: u32) -> u32 {
    let n = graph.node_count();
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

    #[test]
    fn finds_the_same_optimum_as_dijkstra() {
        // Diamond with two paths; A* must pick the shorter one too.
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
}
