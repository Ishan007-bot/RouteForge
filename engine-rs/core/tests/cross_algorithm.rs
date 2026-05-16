//! End-to-end correctness: every algorithm must return the same
//! optimal cost on the same graph + endpoints. Path nodes can differ
//! when ties exist, but `duration_seconds` and `distance_meters` must
//! agree to within float tolerance.

use routeforge_core::{
    astar, bidir, ch, dijkstra,
    graph::{HighwayClass, RoadGraphBuilder},
    profile::CarProfile,
};

fn grid(n: u32) -> routeforge_core::RoadGraph {
    let mut b = RoadGraphBuilder::with_capacity(
        (n * n) as usize,
        (4 * n * n) as usize,
    );
    // Place nodes on a 10m lat/lon-ish step.
    let d = 0.0001;
    for y in 0..n {
        for x in 0..n {
            b.add_node(y as f64 * d, x as f64 * d);
        }
    }
    let idx = |x: u32, y: u32| -> u32 { y * n + x };
    for y in 0..n {
        for x in 0..n {
            let here = idx(x, y);
            if x + 1 < n {
                let nb = idx(x + 1, y);
                b.add_edge(here, nb, 10.0, HighwayClass::Residential, 30);
                b.add_edge(nb, here, 10.0, HighwayClass::Residential, 30);
            }
            if y + 1 < n {
                let nb = idx(x, y + 1);
                b.add_edge(here, nb, 10.0, HighwayClass::Residential, 30);
                b.add_edge(nb, here, 10.0, HighwayClass::Residential, 30);
            }
        }
    }
    b.build().unwrap()
}

#[test]
fn dijkstra_astar_bidir_ch_all_agree_on_a_10x10_grid() {
    let g = grid(10);
    let src = 0;
    let tgt = 99; // corner-to-corner

    let d = dijkstra::shortest_path(&g, src, tgt, &CarProfile);
    let a = astar::shortest_path(&g, src, tgt, &CarProfile);
    let b = bidir::shortest_path(&g, src, tgt, &CarProfile);

    let preped = ch::preprocess(&g, &CarProfile);
    let c = ch::shortest_path(&preped, src, tgt);

    assert!(d.found && a.found && b.found && c.found);

    // Durations must agree (this is the optimization value).
    let eps = 1e-6;
    assert!((d.duration_seconds - a.duration_seconds).abs() < eps,
        "A* disagrees with Dijkstra: {} vs {}", a.duration_seconds, d.duration_seconds);
    assert!((d.duration_seconds - b.duration_seconds).abs() < eps,
        "Bidir disagrees with Dijkstra: {} vs {}", b.duration_seconds, d.duration_seconds);
    assert!((d.duration_seconds - c.duration_seconds).abs() < eps,
        "CH disagrees with Dijkstra: {} vs {}", c.duration_seconds, d.duration_seconds);

    // Distances should also agree (path may differ when costs tie, but
    // on this uniform grid corner-to-corner the manhattan distance fixes it).
    let dist_eps = 1.0;
    assert!((d.distance_meters - a.distance_meters).abs() < dist_eps);
    assert!((d.distance_meters - b.distance_meters).abs() < dist_eps);
    assert!((d.distance_meters - c.distance_meters).abs() < dist_eps);
}

#[test]
fn ch_returns_a_valid_node_path_when_unpacked() {
    let g = grid(8);
    let preped = ch::preprocess(&g, &CarProfile);
    let r = ch::shortest_path(&preped, 0, 63);
    assert!(r.found);

    // Every consecutive pair of nodes in the unpacked path must be a
    // true edge in the original graph — that's the proof shortcuts
    // unpacked correctly.
    for w in r.node_path.windows(2) {
        let (u, v) = (w[0], w[1]);
        let mut found = false;
        for e in g.out_edges(u) {
            if g.target(e) == v { found = true; break; }
        }
        assert!(found, "unpacked CH path has phantom edge {u}->{v}");
    }
}
