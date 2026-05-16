//! Contraction Hierarchies.
//!
//! Preprocessing builds an augmented graph where every edge points to
//! a strictly-more-important node. Queries are bidirectional
//! Dijkstras that only walk "upward" edges; on real road networks
//! this is a few-hundred-x speedup over plain Dijkstra. The path is
//! reconstructed by recursively unpacking shortcut edges into the two
//! original edges they replace.
//!
//! ## Implementation notes
//!
//! * **Edges live in a single id namespace.** Original edges first,
//!   then shortcuts appended during preprocessing. Each edge stores
//!   its two children if it's a shortcut, or `(-1, -1)` if it's an
//!   original — unpacking is just recursion on those pointers.
//! * **Ordering** is computed once up front (edge-difference
//!   heuristic) and used as a fixed schedule. The Java port found
//!   that lazy re-ordering can deadlock on ties; the fixed schedule
//!   trades a small amount of preprocessing quality for guaranteed
//!   termination.
//! * **Witness search** is a bounded Dijkstra (hop and cost limits).
//!   If a path of cost ≤ `cost(u_in→u→u_out)` exists that avoids
//!   `u`, no shortcut is needed.

use std::time::Instant;

use crate::dijkstra::RouteResult;
use crate::graph::RoadGraph;
use crate::heap::IndexedBinaryHeap;
use crate::profile::Profile;

const NO_CHILD: i32 = -1;
const WITNESS_MAX_HOPS: u32 = 5;

#[derive(Clone, Debug)]
struct CHEdge {
    from: u32,
    to:   u32,
    cost: f64,
    length_m: f32,
    /// For shortcuts: the two child edges (`child_a` is the first
    /// half, `child_b` is the second). `NO_CHILD` for originals.
    child_a: i32,
    child_b: i32,
}

/// Preprocessed graph ready for queries. Holds:
/// - the augmented edge list (originals + shortcuts)
/// - level per node (contraction order)
/// - "upward" CSR adjacencies for both endpoints (for the bidir query)
pub struct CHGraph {
    nodes: usize,
    edges: Vec<CHEdge>,
    /// Retained for future query variants (e.g. shortcut-priority
    /// traversal). The two CSR adjacencies already bake in direction
    /// for the standard bidirectional query.
    #[allow(dead_code)]
    level: Vec<u32>,

    /// `up_out[u]`: edge ids `(u → v)` with `level[v] > level[u]`. Used by
    /// the forward search to climb the hierarchy.
    up_out_first: Vec<u32>,
    up_out_edges: Vec<u32>,
    /// `down_in[v]`: edge ids `(u → v)` with `level[u] > level[v]`. Used by
    /// the reverse search, which starts at `target` and walks these edges
    /// backward to also climb the hierarchy.
    down_in_first: Vec<u32>,
    down_in_edges: Vec<u32>,

    /// Lat/lon per node, copied from the source road graph so the CH
    /// query can build geometry without re-borrowing the graph.
    node_lat: Vec<f64>,
    node_lon: Vec<f64>,
}

impl CHGraph {
    pub fn node_count(&self) -> usize { self.nodes }
    pub fn edge_count(&self) -> usize { self.edges.len() }
    pub fn shortcut_count(&self) -> usize {
        self.edges.iter().filter(|e| e.child_a != NO_CHILD).count()
    }
}

/* =========================================================
   Preprocessor
   ========================================================= */

pub fn preprocess(graph: &RoadGraph, profile: &dyn Profile) -> CHGraph {
    let n = graph.node_count();
    let mut edges: Vec<CHEdge> = Vec::with_capacity(graph.edge_count() * 2);
    let mut out_adj: Vec<Vec<u32>> = vec![Vec::new(); n];
    let mut in_adj:  Vec<Vec<u32>> = vec![Vec::new(); n];

    // Seed from the original graph's forward edges.
    for u in 0..n as u32 {
        for e in graph.out_edges(u) {
            let v = graph.target(e);
            let c = profile.cost(graph, e);
            if !c.is_finite() { continue; }
            let id = edges.len() as u32;
            edges.push(CHEdge {
                from: u, to: v, cost: c,
                length_m: graph.length_meters(e) as f32,
                child_a: NO_CHILD, child_b: NO_CHILD,
            });
            out_adj[u as usize].push(id);
            in_adj[v as usize].push(id);
        }
    }

    // Initial importance = simulated edge difference.
    let mut importance: Vec<i64> = (0..n as u32)
        .map(|u| edge_difference(u, &out_adj, &in_adj, &edges) as i64)
        .collect();

    // Sort nodes by importance ascending, ties broken by id for determinism.
    let mut order: Vec<u32> = (0..n as u32).collect();
    order.sort_by_key(|&u| (importance[u as usize], u));

    let mut contracted: Vec<bool> = vec![false; n];
    let mut level: Vec<u32> = vec![0; n];

    for (lvl, &u) in order.iter().enumerate() {
        contract_node(u, &mut out_adj, &mut in_adj, &mut edges, &contracted);
        contracted[u as usize] = true;
        level[u as usize] = lvl as u32;
        // Silence unused warning — importance is only used for ordering.
        let _ = &mut importance;
    }

    // Build the two CSRs that the bidirectional query needs.
    //
    // Forward search: at u, follow edges going UP — `(u → v)` with
    // level[v] > level[u]. Index by `from`.
    //
    // Reverse search: starts at target, also climbs UP. At v, it
    // walks edges `(u → v)` BACKWARD where level[u] > level[v] (so
    // stepping backward lands on a *higher-level* node u). Index by `to`.
    let mut up_out_count   = vec![0u32; n + 1];
    let mut down_in_count  = vec![0u32; n + 1];
    for e in &edges {
        let lf = level[e.from as usize];
        let lt = level[e.to   as usize];
        if lt > lf {
            up_out_count[e.from as usize + 1] += 1;
        } else if lf > lt {
            down_in_count[e.to as usize + 1] += 1;
        }
    }
    for i in 1..=n { up_out_count[i]  += up_out_count[i - 1]; }
    for i in 1..=n { down_in_count[i] += down_in_count[i - 1]; }
    let up_out_first  = up_out_count.clone();
    let down_in_first = down_in_count.clone();

    let mut up_out_edges  = vec![0u32; up_out_first[n]  as usize];
    let mut down_in_edges = vec![0u32; down_in_first[n] as usize];
    let mut up_out_cursor  = up_out_first.clone();
    let mut down_in_cursor = down_in_first.clone();
    for (id, e) in edges.iter().enumerate() {
        let lf = level[e.from as usize];
        let lt = level[e.to   as usize];
        if lt > lf {
            let p = up_out_cursor[e.from as usize] as usize;
            up_out_edges[p] = id as u32;
            up_out_cursor[e.from as usize] += 1;
        } else if lf > lt {
            let p = down_in_cursor[e.to as usize] as usize;
            down_in_edges[p] = id as u32;
            down_in_cursor[e.to as usize] += 1;
        }
    }

    let node_lat: Vec<f64> = (0..n as u32).map(|u| graph.lat(u)).collect();
    let node_lon: Vec<f64> = (0..n as u32).map(|u| graph.lon(u)).collect();

    CHGraph {
        nodes: n, edges, level,
        up_out_first, up_out_edges,
        down_in_first, down_in_edges,
        node_lat, node_lon,
    }
}

/// Simulate contracting `u` and return the number of shortcuts that
/// would be added. Used purely for ordering.
fn edge_difference(
    u: u32,
    out_adj: &[Vec<u32>],
    in_adj:  &[Vec<u32>],
    edges:   &[CHEdge],
) -> i64 {
    let removed = (out_adj[u as usize].len() + in_adj[u as usize].len()) as i64;
    let mut added = 0i64;
    for &in_e in &in_adj[u as usize] {
        let w = edges[in_e as usize].from;
        for &out_e in &out_adj[u as usize] {
            let x = edges[out_e as usize].to;
            if w == x { continue; }
            let direct = edges[in_e as usize].cost + edges[out_e as usize].cost;
            // For ordering, skip witness search and assume worst case (shortcut needed).
            // This is a coarse but fast estimate.
            let _ = direct;
            added += 1;
        }
    }
    added - removed
}

fn contract_node(
    u: u32,
    out_adj: &mut [Vec<u32>],
    in_adj:  &mut [Vec<u32>],
    edges:   &mut Vec<CHEdge>,
    contracted: &[bool],
) {
    // Snapshot u's incident edges — we'll be mutating the vectors.
    let in_edges:  Vec<u32> = in_adj[u as usize].clone();
    let out_edges: Vec<u32> = out_adj[u as usize].clone();

    for &in_e in &in_edges {
        let w = edges[in_e as usize].from;
        if contracted[w as usize] { continue; }
        for &out_e in &out_edges {
            let x = edges[out_e as usize].to;
            if x == w || contracted[x as usize] { continue; }
            let direct = edges[in_e as usize].cost + edges[out_e as usize].cost;

            if witness_path_exists(w, x, u, direct, out_adj, edges, contracted) {
                continue;
            }

            // Add shortcut w -> x with cost = direct.
            let length_m = edges[in_e as usize].length_m + edges[out_e as usize].length_m;
            let id = edges.len() as u32;
            edges.push(CHEdge {
                from: w, to: x, cost: direct, length_m,
                child_a: in_e as i32, child_b: out_e as i32,
            });
            out_adj[w as usize].push(id);
            in_adj[x as usize].push(id);
        }
    }
}

/// Bounded Dijkstra from `src` to `tgt` avoiding `skip` and any
/// already-contracted node. Returns true if a path of cost ≤ `max_cost`
/// exists.
fn witness_path_exists(
    src: u32,
    tgt: u32,
    skip: u32,
    max_cost: f64,
    out_adj: &[Vec<u32>],
    edges:   &[CHEdge],
    contracted: &[bool],
) -> bool {
    // A flat HashMap-like via Vec<f64> sized to the global node count would
    // cost too much per call; use a Vec with stamps instead.
    // For a witness search we expect to settle very few nodes, so a small
    // ad-hoc map keyed by node id is fine.
    let mut dist: std::collections::HashMap<u32, (f64, u32)> = std::collections::HashMap::new();
    let mut heap: std::collections::BinaryHeap<HeapEntry> = std::collections::BinaryHeap::new();
    dist.insert(src, (0.0, 0));
    heap.push(HeapEntry { key: 0.0, hops: 0, node: src });

    while let Some(HeapEntry { key, hops, node: u }) = heap.pop() {
        if key > max_cost { return false; }
        if u == tgt && key <= max_cost { return true; }
        if hops >= WITNESS_MAX_HOPS { continue; }
        // Skip stale entries.
        if let Some(&(d, _)) = dist.get(&u) { if d < key { continue; } }
        for &e_id in &out_adj[u as usize] {
            let e = &edges[e_id as usize];
            let v = e.to;
            if v == skip || contracted[v as usize] { continue; }
            let alt = key + e.cost;
            if alt > max_cost { continue; }
            let better = match dist.get(&v) {
                Some(&(d, _)) => alt < d,
                None => true,
            };
            if better {
                dist.insert(v, (alt, hops + 1));
                heap.push(HeapEntry { key: alt, hops: hops + 1, node: v });
            }
        }
    }
    false
}

#[derive(Clone, Copy)]
struct HeapEntry { key: f64, hops: u32, node: u32 }
impl PartialEq for HeapEntry { fn eq(&self, o: &Self) -> bool { self.key == o.key } }
impl Eq for HeapEntry {}
impl PartialOrd for HeapEntry {
    fn partial_cmp(&self, o: &Self) -> Option<std::cmp::Ordering> { Some(self.cmp(o)) }
}
impl Ord for HeapEntry {
    // Min-heap via reversed comparison; std::collections::BinaryHeap is max-heap.
    fn cmp(&self, o: &Self) -> std::cmp::Ordering { o.key.partial_cmp(&self.key).unwrap_or(std::cmp::Ordering::Equal) }
}

/* =========================================================
   Query
   ========================================================= */

pub fn shortest_path(
    ch: &CHGraph,
    source: u32,
    target: u32,
) -> RouteResult {
    let start = Instant::now();
    let n = ch.nodes;
    if n == 0 || (source as usize) >= n || (target as usize) >= n {
        return not_found(start, 0);
    }
    if source == target {
        return RouteResult {
            found: true, node_path: vec![source],
            geometry: vec![(ch.node_lat[source as usize], ch.node_lon[source as usize])],
            distance_meters: 0.0, duration_seconds: 0.0,
            algorithm: "ch", elapsed_micros: start.elapsed().as_micros(),
            nodes_settled: 0,
        };
    }

    let mut fwd_dist = vec![f64::INFINITY; n];
    let mut bwd_dist = vec![f64::INFINITY; n];
    let mut fwd_prev = vec![-1i32; n]; // edge id used to relax this node from forward side
    let mut bwd_prev = vec![-1i32; n]; // edge id used to relax this node from reverse side
    let mut fwd_heap = IndexedBinaryHeap::with_capacity(n);
    let mut bwd_heap = IndexedBinaryHeap::with_capacity(n);

    fwd_dist[source as usize] = 0.0;
    bwd_dist[target as usize] = 0.0;
    fwd_heap.push_or_decrease(source, 0.0);
    bwd_heap.push_or_decrease(target, 0.0);

    let mut best = f64::INFINITY;
    let mut meeting: u32 = u32::MAX;
    let mut settled = 0u64;

    loop {
        let fwd_top = fwd_heap.peek_min().map(|u| fwd_heap.key_of(u));
        let bwd_top = bwd_heap.peek_min().map(|u| bwd_heap.key_of(u));

        match (fwd_top, bwd_top) {
            (None, None) => break,
            (Some(f), Some(b)) if f.min(b) >= best => break,
            _ => {}
        }

        let do_fwd = match (fwd_top, bwd_top) {
            (Some(f), Some(b)) => f <= b,
            (Some(_), None) => true,
            (None, Some(_)) => false,
            _ => break,
        };

        if do_fwd {
            let u = fwd_heap.pop_min().unwrap();
            settled += 1;
            let g_u = fwd_dist[u as usize];
            if bwd_dist[u as usize].is_finite() {
                let c = g_u + bwd_dist[u as usize];
                if c < best { best = c; meeting = u; }
            }
            for ei in ch.up_out_first[u as usize]..ch.up_out_first[u as usize + 1] {
                let e = ch.up_out_edges[ei as usize];
                let edge = &ch.edges[e as usize];
                let v = edge.to;
                let alt = g_u + edge.cost;
                if alt < fwd_dist[v as usize] {
                    fwd_dist[v as usize] = alt;
                    fwd_prev[v as usize] = e as i32;
                    fwd_heap.push_or_decrease(v, alt);
                    if bwd_dist[v as usize].is_finite() {
                        let c = alt + bwd_dist[v as usize];
                        if c < best { best = c; meeting = v; }
                    }
                }
            }
        } else {
            let v = bwd_heap.pop_min().unwrap();
            settled += 1;
            let g_v = bwd_dist[v as usize];
            if fwd_dist[v as usize].is_finite() {
                let c = fwd_dist[v as usize] + g_v;
                if c < best { best = c; meeting = v; }
            }
            // Walk "downward" incoming edges of v — these are edges (u, v)
            // where level[u] > level[v]. From v, we step backward to u,
            // which sits higher in the hierarchy — both searches climb up.
            for ei in ch.down_in_first[v as usize]..ch.down_in_first[v as usize + 1] {
                let e = ch.down_in_edges[ei as usize];
                let edge = &ch.edges[e as usize];
                let u = edge.from;
                let alt = g_v + edge.cost;
                if alt < bwd_dist[u as usize] {
                    bwd_dist[u as usize] = alt;
                    bwd_prev[u as usize] = e as i32;
                    bwd_heap.push_or_decrease(u, alt);
                    if fwd_dist[u as usize].is_finite() {
                        let c = fwd_dist[u as usize] + alt;
                        if c < best { best = c; meeting = u; }
                    }
                }
            }
        }
    }

    if !best.is_finite() || meeting == u32::MAX {
        return not_found(start, settled);
    }

    // ---- Reconstruct ----
    // Build the up-edge id sequences from source to meeting and from
    // meeting to target, then unpack each shortcut.
    let mut fwd_edge_path: Vec<u32> = Vec::new();
    let mut cur = meeting;
    while cur != source {
        let e = fwd_prev[cur as usize];
        if e < 0 { return not_found(start, settled); }
        fwd_edge_path.push(e as u32);
        cur = ch.edges[e as usize].from;
    }
    fwd_edge_path.reverse();

    let mut bwd_edge_path: Vec<u32> = Vec::new();
    let mut cur = meeting;
    while cur != target {
        let e = bwd_prev[cur as usize];
        if e < 0 { return not_found(start, settled); }
        bwd_edge_path.push(e as u32);
        cur = ch.edges[e as usize].to;
    }

    let mut full_edges = fwd_edge_path;
    full_edges.extend(bwd_edge_path);

    // Unpack shortcuts into original edges.
    let mut unpacked: Vec<u32> = Vec::with_capacity(full_edges.len());
    let mut stack: Vec<u32> = full_edges.into_iter().rev().collect();
    while let Some(e) = stack.pop() {
        let edge = &ch.edges[e as usize];
        if edge.child_a == NO_CHILD {
            unpacked.push(e);
        } else {
            // Push children in reverse so child_a is popped first.
            stack.push(edge.child_b as u32);
            stack.push(edge.child_a as u32);
        }
    }

    // Build node path from edge sequence.
    let mut node_path: Vec<u32> = Vec::with_capacity(unpacked.len() + 1);
    if !unpacked.is_empty() {
        node_path.push(ch.edges[unpacked[0] as usize].from);
        for &e in &unpacked {
            node_path.push(ch.edges[e as usize].to);
        }
    }

    let distance_meters: f64 = unpacked.iter()
        .map(|&e| ch.edges[e as usize].length_m as f64).sum();
    let geometry: Vec<(f64, f64)> = node_path.iter()
        .map(|&n| (ch.node_lat[n as usize], ch.node_lon[n as usize])).collect();

    RouteResult {
        found: true,
        node_path,
        geometry,
        distance_meters,
        duration_seconds: best,
        algorithm: "ch",
        elapsed_micros: start.elapsed().as_micros(),
        nodes_settled: settled,
    }
}

fn not_found(start: Instant, settled: u64) -> RouteResult {
    RouteResult {
        found: false, node_path: Vec::new(), geometry: Vec::new(),
        distance_meters: 0.0, duration_seconds: 0.0,
        algorithm: "ch", elapsed_micros: start.elapsed().as_micros(),
        nodes_settled: settled,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::graph::{HighwayClass, RoadGraphBuilder};
    use crate::profile::CarProfile;

    #[test]
    fn matches_dijkstra_on_a_small_diamond() {
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
        let ch = preprocess(&g, &CarProfile);
        let r = shortest_path(&ch, n0, n3);
        assert!(r.found);
        assert!((r.distance_meters - 50.0).abs() < 1e-3);
        assert_eq!(r.node_path.first().copied(), Some(n0));
        assert_eq!(r.node_path.last().copied(),  Some(n3));
    }
}
