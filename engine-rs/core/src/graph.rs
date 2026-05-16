//! Compressed-sparse-row road graph — the Rust analogue of the Java
//! `RoadGraph`.
//!
//! Nodes carry `(lat, lon)` directly via parallel `Vec<f64>` arrays.
//! Edges are stored in CSR form so iterating the outgoing edges of a
//! node is a flat memory scan with no pointer chasing — cache friendly
//! the same way the Java arrays are, but without object headers.
//!
//! For phase 6a we only store the outgoing CSR — that's all Dijkstra
//! needs. Reverse adjacency and incoming-edge metadata will land with
//! the bidirectional / CH ports in 6b.

use crate::error::{Result, RouteForgeError};

/// OSM-derived classification of an edge. The variant value (`u8`) is
/// kept low so a `Vec<HighwayClass>` packs tightly without padding.
///
/// Order matches the Java enum so we can swap loaders later.
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HighwayClass {
    Motorway      = 0,
    Trunk         = 1,
    Primary       = 2,
    Secondary     = 3,
    Tertiary      = 4,
    Unclassified  = 5,
    Residential   = 6,
    Service       = 7,
    LivingStreet  = 8,
    Pedestrian    = 9,
    Cycleway      = 10,
    Footway       = 11,
    Path          = 12,
    Track         = 13,
}

impl HighwayClass {
    /// Parse the OSM `highway=*` tag value. Unknown values map to None
    /// and the loader should drop the way.
    pub fn from_osm_tag(value: &str) -> Option<Self> {
        Some(match value {
            "motorway"      | "motorway_link"  => HighwayClass::Motorway,
            "trunk"         | "trunk_link"     => HighwayClass::Trunk,
            "primary"       | "primary_link"   => HighwayClass::Primary,
            "secondary"     | "secondary_link" => HighwayClass::Secondary,
            "tertiary"      | "tertiary_link"  => HighwayClass::Tertiary,
            "unclassified"                      => HighwayClass::Unclassified,
            "residential"                       => HighwayClass::Residential,
            "service"                           => HighwayClass::Service,
            "living_street"                     => HighwayClass::LivingStreet,
            "pedestrian"                        => HighwayClass::Pedestrian,
            "cycleway"                          => HighwayClass::Cycleway,
            "footway"                           => HighwayClass::Footway,
            "path"                              => HighwayClass::Path,
            "track"                             => HighwayClass::Track,
            _ => return None,
        })
    }

    /// Default speed in km/h when the way has no `maxspeed` tag.
    /// Numbers match the Java defaults so routes agree across engines.
    pub fn default_speed_kmh(self) -> u16 {
        match self {
            HighwayClass::Motorway     => 110,
            HighwayClass::Trunk        => 90,
            HighwayClass::Primary      => 70,
            HighwayClass::Secondary    => 60,
            HighwayClass::Tertiary     => 50,
            HighwayClass::Unclassified => 40,
            HighwayClass::Residential  => 30,
            HighwayClass::LivingStreet => 15,
            HighwayClass::Service      => 20,
            HighwayClass::Pedestrian
            | HighwayClass::Cycleway
            | HighwayClass::Footway
            | HighwayClass::Path
            | HighwayClass::Track      => 8,
        }
    }
}

/// A compressed-sparse-row directed graph.
///
/// Invariants (checked by [`RoadGraphBuilder::build`]):
/// - `first_edge.len() == node_count + 1`
/// - `first_edge[node_count] == target.len()`
/// - all four edge-parallel arrays have the same length
pub struct RoadGraph {
    // --- nodes ---
    node_lat: Vec<f64>,
    node_lon: Vec<f64>,

    // --- CSR outgoing adjacency ---
    first_edge: Vec<u32>,    // size: node_count + 1
    target:     Vec<u32>,    // size: edge_count
    length_m:   Vec<f32>,    // size: edge_count
    highway:    Vec<HighwayClass>, // size: edge_count
    max_kmh:    Vec<u16>,    // size: edge_count
}

impl RoadGraph {
    #[inline] pub fn node_count(&self) -> usize { self.node_lat.len() }
    #[inline] pub fn edge_count(&self) -> usize { self.target.len() }

    #[inline] pub fn lat(&self, node: u32) -> f64 { self.node_lat[node as usize] }
    #[inline] pub fn lon(&self, node: u32) -> f64 { self.node_lon[node as usize] }

    #[inline]
    pub fn first_edge(&self, node: u32) -> u32 { self.first_edge[node as usize] }
    #[inline]
    pub fn end_edge(&self, node: u32) -> u32 { self.first_edge[node as usize + 1] }

    #[inline] pub fn target(&self, edge: u32) -> u32 { self.target[edge as usize] }
    #[inline] pub fn length_meters(&self, edge: u32) -> f64 { self.length_m[edge as usize] as f64 }
    #[inline] pub fn highway_class(&self, edge: u32) -> HighwayClass { self.highway[edge as usize] }
    #[inline] pub fn max_speed_kmh(&self, edge: u32) -> u16 { self.max_kmh[edge as usize] }

    /// Iterate the outgoing edge indices for `node`.
    #[inline]
    pub fn out_edges(&self, node: u32) -> std::ops::Range<u32> {
        self.first_edge(node)..self.end_edge(node)
    }

    /// Brute-force nearest-node search. O(n) per call; fine for the few
    /// queries the CLI makes. A KD-tree can replace this later without
    /// changing the signature.
    ///
    /// Returns `None` only for an empty graph.
    pub fn nearest_node(&self, lat: f64, lon: f64) -> Option<u32> {
        if self.node_lat.is_empty() { return None; }
        // Squared planar distance is fine — we just need the argmin and
        // we're comparing nearby points within one city, where the
        // distortion is negligible.
        let mut best_i = 0usize;
        let mut best_d = f64::INFINITY;
        for i in 0..self.node_lat.len() {
            let dlat = self.node_lat[i] - lat;
            let dlon = self.node_lon[i] - lon;
            let d = dlat * dlat + dlon * dlon;
            if d < best_d { best_d = d; best_i = i; }
        }
        Some(best_i as u32)
    }
}

/// Mutable builder. Add nodes, then add edges (each edge directional —
/// add the reverse explicitly if the road is two-way), then `build()`.
pub struct RoadGraphBuilder {
    node_lat: Vec<f64>,
    node_lon: Vec<f64>,
    // Edges as a flat list; we sort by source on build for CSR layout.
    edges: Vec<EdgeBuf>,
}

struct EdgeBuf {
    source:   u32,
    target:   u32,
    length_m: f32,
    highway:  HighwayClass,
    max_kmh:  u16,
}

impl Default for RoadGraphBuilder {
    fn default() -> Self { Self::new() }
}

impl RoadGraphBuilder {
    pub fn new() -> Self {
        Self { node_lat: Vec::new(), node_lon: Vec::new(), edges: Vec::new() }
    }

    pub fn with_capacity(nodes: usize, edges: usize) -> Self {
        Self {
            node_lat: Vec::with_capacity(nodes),
            node_lon: Vec::with_capacity(nodes),
            edges:    Vec::with_capacity(edges),
        }
    }

    /// Append a node. Returns its index.
    pub fn add_node(&mut self, lat: f64, lon: f64) -> u32 {
        let idx = self.node_lat.len() as u32;
        self.node_lat.push(lat);
        self.node_lon.push(lon);
        idx
    }

    /// Append a directional edge. Both endpoints must refer to nodes
    /// already added with [`add_node`].
    pub fn add_edge(
        &mut self, source: u32, target: u32,
        length_m: f32, highway: HighwayClass, max_kmh: u16,
    ) {
        self.edges.push(EdgeBuf { source, target, length_m, highway, max_kmh });
    }

    pub fn build(self) -> Result<RoadGraph> {
        let n = self.node_lat.len();
        if n == 0 { return Err(RouteForgeError::InvalidGraph("no nodes".into())); }
        for e in &self.edges {
            if (e.source as usize) >= n || (e.target as usize) >= n {
                return Err(RouteForgeError::InvalidGraph(
                    format!("edge references unknown node: {} -> {}", e.source, e.target),
                ));
            }
        }

        // Bucket-sort edges by source so the CSR layout falls out naturally.
        let mut counts = vec![0u32; n + 1];
        for e in &self.edges { counts[e.source as usize + 1] += 1; }
        // Prefix sum -> first_edge.
        for i in 1..=n { counts[i] += counts[i - 1]; }
        let first_edge = counts; // last entry == edge_count

        let edge_count = self.edges.len();
        let mut target   = vec![0u32; edge_count];
        let mut length_m = vec![0f32; edge_count];
        let mut highway  = vec![HighwayClass::Residential; edge_count];
        let mut max_kmh  = vec![0u16; edge_count];

        // Write cursor per source bucket.
        let mut cursor = first_edge.clone();
        for e in &self.edges {
            let pos = cursor[e.source as usize] as usize;
            target[pos]   = e.target;
            length_m[pos] = e.length_m;
            highway[pos]  = e.highway;
            max_kmh[pos]  = e.max_kmh;
            cursor[e.source as usize] += 1;
        }

        Ok(RoadGraph {
            node_lat: self.node_lat,
            node_lon: self.node_lon,
            first_edge, target, length_m, highway, max_kmh,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn triangle() -> RoadGraph {
        let mut b = RoadGraphBuilder::new();
        let a = b.add_node(48.0,    11.0);
        let c = b.add_node(48.001,  11.0);
        let d = b.add_node(48.0005, 11.001);
        b.add_edge(a, c, 100.0, HighwayClass::Residential, 30);
        b.add_edge(c, a, 100.0, HighwayClass::Residential, 30);
        b.add_edge(c, d, 100.0, HighwayClass::Residential, 30);
        b.add_edge(d, c, 100.0, HighwayClass::Residential, 30);
        b.add_edge(a, d, 200.0, HighwayClass::Residential, 30);
        b.add_edge(d, a, 200.0, HighwayClass::Residential, 30);
        b.build().unwrap()
    }

    #[test]
    fn csr_offsets_are_monotonic_and_terminate_at_edge_count() {
        let g = triangle();
        assert_eq!(g.node_count(), 3);
        assert_eq!(g.edge_count(), 6);
        for n in 0..g.node_count() as u32 {
            assert!(g.first_edge(n) <= g.end_edge(n));
        }
        assert_eq!(g.first_edge[g.node_count()] as usize, g.edge_count());
    }

    #[test]
    fn out_edges_yield_the_correct_targets() {
        let g = triangle();
        let mut targets: Vec<u32> = g.out_edges(0).map(|e| g.target(e)).collect();
        targets.sort();
        assert_eq!(targets, vec![1, 2]);
    }

    #[test]
    fn nearest_node_picks_the_closest() {
        let g = triangle();
        let n = g.nearest_node(48.0006, 11.0009).unwrap();
        assert_eq!(n, 2);
    }
}
