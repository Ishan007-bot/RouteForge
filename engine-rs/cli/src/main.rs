//! RouteForge Rust CLI (phase 6a).
//!
//! Subcommands:
//!   * `route` — build a synthetic grid and run a single Dijkstra query
//!   * `bench` — same, but run the query N times and print latency stats
//!
//! Real PBF loading is deferred to a later phase — see the note in
//! `cli/Cargo.toml`.

mod grid;

use std::time::Instant;

use anyhow::{anyhow, Result};
use clap::{Parser, Subcommand};

use routeforge_core::{profile_by_name, shortest_path, RoadGraph};

#[derive(Parser, Debug)]
#[command(name = "routeforge", about = "RouteForge Rust CLI", version)]
struct Args {
    #[command(subcommand)]
    cmd: Cmd,
}

#[derive(Subcommand, Debug)]
enum Cmd {
    /// Plan a single route from (from-x, from-y) to (to-x, to-y) on the grid.
    Route(RouteArgs),
    /// Run the same route many times and print latency stats.
    Bench(BenchArgs),
}

#[derive(Parser, Debug, Clone)]
struct GridArgs {
    /// Grid side length (n). Produces n² nodes and ~4 n² edges.
    #[arg(long, default_value_t = 100)]
    grid_size: u32,
    /// Spacing between adjacent grid nodes, in metres.
    #[arg(long, default_value_t = 20.0)]
    step_meters: f64,
    /// Anchor latitude for the grid origin.
    #[arg(long, default_value_t = 47.154)]
    lat0: f64,
    /// Anchor longitude for the grid origin.
    #[arg(long, default_value_t = 9.5215)]
    lon0: f64,
}

#[derive(Parser, Debug)]
struct RouteArgs {
    #[command(flatten)]
    grid: GridArgs,
    /// Travel profile: car, bike, or foot.
    #[arg(long, default_value = "car")]
    profile: String,
    /// Source grid column (defaults to 0).
    #[arg(long, default_value_t = 0)] from_x: u32,
    /// Source grid row (defaults to 0).
    #[arg(long, default_value_t = 0)] from_y: u32,
    /// Target grid column (defaults to grid_size - 1).
    #[arg(long)] to_x: Option<u32>,
    /// Target grid row (defaults to grid_size - 1).
    #[arg(long)] to_y: Option<u32>,
}

#[derive(Parser, Debug)]
struct BenchArgs {
    #[command(flatten)]
    grid: GridArgs,
    #[arg(long, default_value = "car")] profile: String,
    #[arg(long, default_value_t = 0)] from_x: u32,
    #[arg(long, default_value_t = 0)] from_y: u32,
    #[arg(long)] to_x: Option<u32>,
    #[arg(long)] to_y: Option<u32>,
    /// How many timed iterations.
    #[arg(long, default_value_t = 100)] iterations: usize,
    /// Warm-up runs before timing begins.
    #[arg(long, default_value_t = 5)] warmup: usize,
}

fn main() -> Result<()> {
    let args = Args::parse();
    match args.cmd {
        Cmd::Route(a) => cmd_route(a),
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

    let r = shortest_path(&graph, src, tgt, profile.as_ref());

    println!("grid build       : {load_ms} ms ({0}x{0})", a.grid.grid_size);
    println!("nodes / edges    : {} / {}", graph.node_count(), graph.edge_count());
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

    for _ in 0..a.warmup {
        let _ = shortest_path(&graph, src, tgt, profile.as_ref());
    }

    let mut samples: Vec<u128> = Vec::with_capacity(a.iterations);
    let mut last_dist = 0.0;
    let mut last_settled = 0u64;
    for _ in 0..a.iterations {
        let r = shortest_path(&graph, src, tgt, profile.as_ref());
        if !r.found { return Err(anyhow!("no path found between the two grid points")); }
        samples.push(r.elapsed_micros);
        last_dist = r.distance_meters;
        last_settled = r.nodes_settled;
    }

    samples.sort_unstable();
    let n = samples.len();
    let p50 = samples[n / 2];
    let p95 = samples[(n * 95) / 100];
    let p99 = samples[(n * 99) / 100];
    let min = samples[0];
    let max = samples[n - 1];
    let mean = samples.iter().sum::<u128>() as f64 / n as f64;

    println!("profile          : {}", a.profile);
    println!("from -> to (idx) : {src} -> {tgt}");
    println!("distance         : {:.1} m", last_dist);
    println!("nodes settled    : {}", last_settled);
    println!("iterations       : {n}  (warmup: {})", a.warmup);
    println!("latency µs       : min {min}  p50 {p50}  p95 {p95}  p99 {p99}  max {max}");
    println!("latency mean ms  : {:.3}", mean / 1000.0);
    Ok(())
}
