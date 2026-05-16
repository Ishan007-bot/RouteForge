//! RouteForge Rust CLI (phase 6b).
//!
//! Subcommands:
//!   * `route` — single query under a chosen algorithm
//!   * `iso`   — isochrone (one-to-many) from a single node
//!   * `bench` — same query under every algorithm, side-by-side stats
//!
//! Real PBF loading still deferred; the input is a synthetic grid graph
//! (see `cli/Cargo.toml` for why).

mod grid;

use std::time::Instant;

use anyhow::{anyhow, Result};
use clap::{Parser, Subcommand, ValueEnum};

use routeforge_core::{
    astar, bidir, ch, dijkstra, isochrone, profile_by_name, RoadGraph,
};

#[derive(Parser, Debug)]
#[command(name = "routeforge", about = "RouteForge Rust CLI", version)]
struct Args {
    #[command(subcommand)]
    cmd: Cmd,
}

#[derive(Subcommand, Debug)]
enum Cmd {
    /// Plan a single route on the synthetic grid.
    Route(RouteArgs),
    /// Reachability set within a time budget.
    Iso(IsoArgs),
    /// Run the same query under every algorithm and compare latency / work.
    Bench(BenchArgs),
}

#[derive(Parser, Debug, Clone)]
struct GridArgs {
    /// Grid side length (n). n² nodes, ~4n² edges.
    #[arg(long, default_value_t = 100)]
    grid_size: u32,
    /// Spacing between adjacent grid nodes, in metres.
    #[arg(long, default_value_t = 20.0)]
    step_meters: f64,
    /// Anchor latitude.
    #[arg(long, default_value_t = 47.154)]
    lat0: f64,
    /// Anchor longitude.
    #[arg(long, default_value_t = 9.5215)]
    lon0: f64,
}

#[derive(Clone, Copy, Debug, ValueEnum)]
enum Algo { Dijkstra, Astar, Bidir, Ch }

#[derive(Parser, Debug)]
struct RouteArgs {
    #[command(flatten)] grid: GridArgs,
    #[arg(long, default_value = "car")]   profile: String,
    #[arg(long, value_enum, default_value_t = Algo::Dijkstra)] algo: Algo,
    #[arg(long, default_value_t = 0)] from_x: u32,
    #[arg(long, default_value_t = 0)] from_y: u32,
    #[arg(long)] to_x: Option<u32>,
    #[arg(long)] to_y: Option<u32>,
}

#[derive(Parser, Debug)]
struct IsoArgs {
    #[command(flatten)] grid: GridArgs,
    #[arg(long, default_value = "car")] profile: String,
    /// Origin grid column.
    #[arg(long, default_value_t = 50)] x: u32,
    /// Origin grid row.
    #[arg(long, default_value_t = 50)] y: u32,
    /// Time budget in seconds.
    #[arg(long, default_value_t = 30.0)] budget: f64,
}

#[derive(Parser, Debug)]
struct BenchArgs {
    #[command(flatten)] grid: GridArgs,
    #[arg(long, default_value = "car")] profile: String,
    #[arg(long, default_value_t = 0)] from_x: u32,
    #[arg(long, default_value_t = 0)] from_y: u32,
    #[arg(long)] to_x: Option<u32>,
    #[arg(long)] to_y: Option<u32>,
    /// How many timed iterations per algorithm.
    #[arg(long, default_value_t = 50)] iterations: usize,
    #[arg(long, default_value_t = 5)] warmup: usize,
    /// Skip CH (preprocessing is slow on uniform grids).
    #[arg(long, default_value_t = false)] skip_ch: bool,
}

fn main() -> Result<()> {
    let args = Args::parse();
    match args.cmd {
        Cmd::Route(a) => cmd_route(a),
        Cmd::Iso(a)   => cmd_iso(a),
        Cmd::Bench(a) => cmd_bench(a),
    }
}

fn build_grid(a: &GridArgs) -> RoadGraph {
    grid::build(a.grid_size, a.lat0, a.lon0, a.step_meters)
}

fn endpoints(grid: &GridArgs, fx: u32, fy: u32, tx: Option<u32>, ty: Option<u32>) -> Result<(u32, u32)> {
    let last = grid.grid_size.saturating_sub(1);
    let tx = tx.unwrap_or(last);
    let ty = ty.unwrap_or(last);
    if fx >= grid.grid_size || fy >= grid.grid_size || tx >= grid.grid_size || ty >= grid.grid_size {
        return Err(anyhow!("coordinates out of grid bounds (grid is {0}×{0})", grid.grid_size));
    }
    let src = fy * grid.grid_size + fx;
    let tgt = ty * grid.grid_size + tx;
    Ok((src, tgt))
}

fn cmd_route(a: RouteArgs) -> Result<()> {
    let t0 = Instant::now();
    let graph = build_grid(&a.grid);
    let load_ms = t0.elapsed().as_millis();

    let profile = profile_by_name(&a.profile)
        .ok_or_else(|| anyhow!("unknown profile: {}", a.profile))?;
    let (src, tgt) = endpoints(&a.grid, a.from_x, a.from_y, a.to_x, a.to_y)?;

    let r = match a.algo {
        Algo::Dijkstra => dijkstra::shortest_path(&graph, src, tgt, profile.as_ref()),
        Algo::Astar    => astar::shortest_path(&graph, src, tgt, profile.as_ref()),
        Algo::Bidir    => bidir::shortest_path(&graph, src, tgt, profile.as_ref()),
        Algo::Ch       => {
            let pt = Instant::now();
            let cg = ch::preprocess(&graph, profile.as_ref());
            eprintln!("ch preprocess: {} ms ({} shortcuts)",
                pt.elapsed().as_millis(), cg.shortcut_count());
            ch::shortest_path(&cg, src, tgt)
        }
    };

    println!("grid build       : {load_ms} ms ({0}x{0})", a.grid.grid_size);
    println!("nodes / edges    : {} / {}", graph.node_count(), graph.edge_count());
    println!("algorithm        : {}", r.algorithm);
    println!("from -> to (idx) : {src} -> {tgt}");
    if r.found {
        println!("distance         : {:.1} m", r.distance_meters);
        println!("duration         : {:.1} s", r.duration_seconds);
        println!("nodes settled    : {}", r.nodes_settled);
        println!("query time       : {} µs ({:.3} ms)",
                 r.elapsed_micros, r.elapsed_micros as f64 / 1000.0);
        println!("path nodes       : {}", r.node_path.len());
    } else {
        println!("no path found (settled {})", r.nodes_settled);
    }
    Ok(())
}

fn cmd_iso(a: IsoArgs) -> Result<()> {
    let graph = build_grid(&a.grid);
    let profile = profile_by_name(&a.profile)
        .ok_or_else(|| anyhow!("unknown profile: {}", a.profile))?;
    if a.x >= a.grid.grid_size || a.y >= a.grid.grid_size {
        return Err(anyhow!("origin out of grid bounds"));
    }
    let src = a.y * a.grid.grid_size + a.x;
    let r = isochrone::compute(&graph, src, a.budget, profile.as_ref());
    println!("origin (idx)     : {src} ({},{})", a.x, a.y);
    println!("budget           : {:.1} s", a.budget);
    println!("reachable nodes  : {} / {}", r.points.len(), graph.node_count());
    println!("nodes settled    : {}", r.nodes_settled);
    println!("query time       : {} µs ({:.3} ms)",
        r.elapsed_micros, r.elapsed_micros as f64 / 1000.0);
    Ok(())
}

fn cmd_bench(a: BenchArgs) -> Result<()> {
    let t0 = Instant::now();
    let graph = build_grid(&a.grid);
    let n = a.grid.grid_size;
    eprintln!(
        "grid build: {} ms ({n}x{n})  ->  {} nodes, {} edges",
        t0.elapsed().as_millis(), graph.node_count(), graph.edge_count(),
    );

    let profile = profile_by_name(&a.profile)
        .ok_or_else(|| anyhow!("unknown profile: {}", a.profile))?;
    let (src, tgt) = endpoints(&a.grid, a.from_x, a.from_y, a.to_x, a.to_y)?;

    let mut rows: Vec<(&'static str, BenchRow)> = Vec::new();

    rows.push(("dijkstra", run_bench(&graph, src, tgt, &a, |g, s, t|
        dijkstra::shortest_path(g, s, t, profile.as_ref()))));
    rows.push(("astar",    run_bench(&graph, src, tgt, &a, |g, s, t|
        astar::shortest_path(g, s, t, profile.as_ref()))));
    rows.push(("bidir",    run_bench(&graph, src, tgt, &a, |g, s, t|
        bidir::shortest_path(g, s, t, profile.as_ref()))));

    if !a.skip_ch {
        let pt = Instant::now();
        let cg = ch::preprocess(&graph, profile.as_ref());
        let prep_ms = pt.elapsed().as_millis();
        eprintln!("ch preprocess: {prep_ms} ms ({} shortcuts)", cg.shortcut_count());

        // CH query has a different signature; bench it inline.
        for _ in 0..a.warmup { let _ = ch::shortest_path(&cg, src, tgt); }
        let mut samples: Vec<u128> = Vec::with_capacity(a.iterations);
        let mut last_dist = 0.0; let mut last_settled = 0u64;
        for _ in 0..a.iterations {
            let r = ch::shortest_path(&cg, src, tgt);
            if !r.found { return Err(anyhow!("CH: no path found")); }
            samples.push(r.elapsed_micros);
            last_dist = r.distance_meters;
            last_settled = r.nodes_settled;
        }
        rows.push(("ch", summarize(samples, last_dist, last_settled)));
    }

    // ---- table ----
    let baseline = rows[0].1.mean;
    println!();
    println!("{:>10}  {:>8}  {:>9}  {:>8}  {:>8}  {:>8}  {:>10}  {:>8}",
        "algorithm", "dist m", "settled", "mean ms", "p50 ms", "p95 ms", "vs dijkstra", "found");
    println!("{}", "-".repeat(82));
    for (name, r) in &rows {
        let ratio = if r.mean > 0.0 { baseline / r.mean } else { 1.0 };
        println!("{:>10}  {:>8.0}  {:>9}  {:>8.3}  {:>8.3}  {:>8.3}  {:>9.2}x  {:>8}",
            name, r.distance, r.settled,
            r.mean / 1000.0, r.p50 as f64 / 1000.0, r.p95 as f64 / 1000.0,
            ratio, "yes");
    }
    println!();
    println!("(iterations: {}  warmup: {})", a.iterations, a.warmup);
    Ok(())
}

struct BenchRow {
    distance: f64,
    settled:  u64,
    mean:     f64,    // µs
    p50:      u128,
    p95:      u128,
}

fn run_bench<F>(graph: &RoadGraph, src: u32, tgt: u32, a: &BenchArgs, mut f: F) -> BenchRow
where F: FnMut(&RoadGraph, u32, u32) -> routeforge_core::RouteResult {
    for _ in 0..a.warmup { let _ = f(graph, src, tgt); }
    let mut samples: Vec<u128> = Vec::with_capacity(a.iterations);
    let mut last_dist = 0.0; let mut last_settled = 0u64;
    for _ in 0..a.iterations {
        let r = f(graph, src, tgt);
        samples.push(r.elapsed_micros);
        last_dist = r.distance_meters;
        last_settled = r.nodes_settled;
    }
    summarize(samples, last_dist, last_settled)
}

fn summarize(mut samples: Vec<u128>, distance: f64, settled: u64) -> BenchRow {
    samples.sort_unstable();
    let n = samples.len();
    let mean = samples.iter().sum::<u128>() as f64 / n as f64;
    BenchRow {
        distance, settled, mean,
        p50: samples[n / 2],
        p95: samples[(n * 95) / 100],
    }
}
