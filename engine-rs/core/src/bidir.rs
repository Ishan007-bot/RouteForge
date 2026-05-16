//! Bidirectional Dijkstra.
//!
//! Two Dijkstra searches run simultaneously — one outward from
//! `source`, one inward from `target` (over the reverse graph). They
//! meet in the middle, and the best path is the smallest
//! `fwd_dist[v] + bwd_dist[v]` across all touched nodes.
//!
//! Why it's faster than plain Dijkstra: each search settles only
//! "half" the area (a ball of radius d/2 instead of d), so the total
//! work is `~2 · (n/2)` instead of `n` for a uniform graph.
//!
//! **Termination.** Once the sum of the two front-of-heap keys
//! exceeds the best known meeting cost, no improvement is possible —
//! Dijkstra never relaxes an edge to a key below the current top of
//! its own heap. That's our stopping condition.

use std::time::Instant;

use crate::dijkstra::RouteResult;
use crate::graph::RoadGraph;
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
        return not_found(start, 0);
    }
    if source == target {
        return RouteResult {
            found: true, node_path: vec![source],
            geometry: vec![(graph.lat(source), graph.lon(source))],
            distance_meters: 0.0, duration_seconds: 0.0,
            algorithm: "bidir", elapsed_micros: start.elapsed().as_micros(),
            nodes_settled: 0,
        };
    }

    // Forward state.
    let mut fwd_dist: Vec<f64> = vec![f64::INFINITY; n];
    let mut fwd_prev: Vec<i32> = vec![-1; n];   // forward edge taken to reach this node
    let mut fwd_heap = IndexedBinaryHeap::with_capacity(n);
    let mut fwd_settled: Vec<bool> = vec![false; n];

    // Reverse state. `bwd_prev[v]` holds a forward edge id; the next
    // node toward target from v is graph.target(prev_edge).
    let mut bwd_dist: Vec<f64> = vec![f64::INFINITY; n];
    let mut bwd_prev: Vec<i32> = vec![-1; n];
    let mut bwd_heap = IndexedBinaryHeap::with_capacity(n);
    let mut bwd_settled: Vec<bool> = vec![false; n];

    fwd_dist[source as usize] = 0.0;
    bwd_dist[target as usize] = 0.0;
    fwd_heap.push_or_decrease(source, 0.0);
    bwd_heap.push_or_decrease(target, 0.0);

    let mut best_cost: f64 = f64::INFINITY;
    let mut meeting_node: u32 = u32::MAX;
    let mut settled_total = 0u64;

    while !fwd_heap.is_empty() && !bwd_heap.is_empty() {
        let fwd_top = fwd_heap.key_of(fwd_heap.peek_min().unwrap());
        let bwd_top = bwd_heap.key_of(bwd_heap.peek_min().unwrap());
        if fwd_top + bwd_top >= best_cost { break; }

        // Step the smaller frontier — keeps both searches balanced.
        if fwd_top <= bwd_top {
            let u = fwd_heap.pop_min().unwrap();
            settled_total += 1;
            fwd_settled[u as usize] = true;
            // Possible meeting: u known to backward as well?
            if bwd_dist[u as usize].is_finite() {
                let cost = fwd_dist[u as usize] + bwd_dist[u as usize];
                if cost < best_cost { best_cost = cost; meeting_node = u; }
            }
            let g_u = fwd_dist[u as usize];
            for e in graph.out_edges(u) {
                let c = profile.cost(graph, e);
                if !c.is_finite() { continue; }
                let v = graph.target(e);
                if fwd_settled[v as usize] { continue; }
                let alt = g_u + c;
                if alt < fwd_dist[v as usize] {
                    fwd_dist[v as usize] = alt;
                    fwd_prev[v as usize] = e as i32;
                    fwd_heap.push_or_decrease(v, alt);
                    if bwd_dist[v as usize].is_finite() {
                        let cost = alt + bwd_dist[v as usize];
                        if cost < best_cost { best_cost = cost; meeting_node = v; }
                    }
                }
            }
        } else {
            let v = bwd_heap.pop_min().unwrap();
            settled_total += 1;
            bwd_settled[v as usize] = true;
            if fwd_dist[v as usize].is_finite() {
                let cost = fwd_dist[v as usize] + bwd_dist[v as usize];
                if cost < best_cost { best_cost = cost; meeting_node = v; }
            }
            let g_v = bwd_dist[v as usize];
            for slot in graph.in_edges(v) {
                let u = graph.in_source(slot);
                let fwd_e = graph.in_fwd_index(slot);
                let c = profile.cost(graph, fwd_e);
                if !c.is_finite() { continue; }
                if bwd_settled[u as usize] { continue; }
                let alt = g_v + c;
                if alt < bwd_dist[u as usize] {
                    bwd_dist[u as usize] = alt;
                    bwd_prev[u as usize] = fwd_e as i32;
                    bwd_heap.push_or_decrease(u, alt);
                    if fwd_dist[u as usize].is_finite() {
                        let cost = fwd_dist[u as usize] + alt;
                        if cost < best_cost { best_cost = cost; meeting_node = u; }
                    }
                }
            }
        }
    }

    if !best_cost.is_finite() || meeting_node == u32::MAX {
        return not_found(start, settled_total);
    }

    // Reconstruct: source → ... → meeting via fwd_prev,
    //              meeting → ... → target via bwd_prev.
    let mut left: Vec<u32> = Vec::new();
    let mut cur = meeting_node;
    loop {
        left.push(cur);
        if cur == source { break; }
        let e = fwd_prev[cur as usize];
        if e < 0 { return not_found(start, settled_total); }
        cur = source_of_edge(graph, e as u32);
    }
    left.reverse();

    let mut right: Vec<u32> = Vec::new();
    let mut cur = meeting_node;
    while cur != target {
        let e = bwd_prev[cur as usize];
        if e < 0 { return not_found(start, settled_total); }
        cur = graph.target(e as u32);
        right.push(cur);
    }

    let mut node_path = left;
    node_path.extend(right);

    let mut distance_meters = 0.0;
    for w in node_path.windows(2) {
        let (u, v) = (w[0], w[1]);
        // Find the forward edge u -> v. With small fan-outs this is O(deg).
        let mut found = false;
        for e in graph.out_edges(u) {
            if graph.target(e) == v {
                distance_meters += graph.length_meters(e);
                found = true; break;
            }
        }
        debug_assert!(found, "reconstructed pair {u}->{v} has no edge");
    }

    let geometry: Vec<(f64, f64)> =
        node_path.iter().map(|&nd| (graph.lat(nd), graph.lon(nd))).collect();

    RouteResult {
        found: true,
        node_path,
        geometry,
        distance_meters,
        duration_seconds: best_cost,
        algorithm: "bidir",
        elapsed_micros: start.elapsed().as_micros(),
        nodes_settled: settled_total,
    }
}

fn not_found(start: Instant, settled: u64) -> RouteResult {
    RouteResult {
        found: false, node_path: Vec::new(), geometry: Vec::new(),
        distance_meters: 0.0, duration_seconds: 0.0,
        algorithm: "bidir", elapsed_micros: start.elapsed().as_micros(),
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

