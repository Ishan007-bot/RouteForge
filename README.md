# RouteForge

A route planner and traffic simulator. Built in Java (engine + API) with a React + MapLibre frontend.
Engine is later ported to Rust behind the same API for a ~2-3x speedup.

## Status

Phase 1 — Routing Core. CSR graph, three profiles (car/bike/foot), three algorithms
(Dijkstra, A*, bidirectional Dijkstra) with an indexed binary heap. CLI takes a
real `.osm.pbf` and lat/lon coordinates, returns a JSON route.

## Repository layout

```
.
├── engine/        # Pure-Java routing engine: graph, profiles, A* / Dijkstra / CH
├── api/           # Spring Boot REST + WebSocket service (Phase 3+)
├── web/           # React + MapLibre frontend (Phase 4+)
├── data/          # OSM .pbf files, GTFS feeds (gitignored, you provide)
├── docker-compose.yml
├── .github/workflows/   # CI
└── pom.xml        # Parent Maven POM
```

## Prerequisites

- JDK 21 or newer (you have 25 — fine)
- Git
- (Later) Docker for Postgres + Redis
- (Later) Node 20+ for the frontend

You do **not** need Maven installed — the project uses the Maven Wrapper (`mvnw`).

## Quick start (Phase 0)

```powershell
# From the project root, on Windows:
.\mvnw.cmd verify
```

Or on macOS/Linux:

```bash
./mvnw verify
```

This compiles the engine, runs unit tests, and prints a summary.

## Try the OSM loader (Phase 0)

Download a small OSM extract (e.g. Liechtenstein, ~1.5 MB):

- https://download.geofabrik.de/europe/liechtenstein.html

Save it to `data/liechtenstein-latest.osm.pbf`, then:

```powershell
.\mvnw.cmd -pl engine compile exec:java `
  "-Dexec.mainClass=com.routeforge.engine.osm.OsmPbfLoader" `
  "-Dexec.args=data/liechtenstein-latest.osm.pbf"
```

Prints counts of nodes, ways, and relations.

## Try the router (Phase 1)

Same `.osm.pbf` file, then plan a route between two coordinates:

```powershell
.\mvnw.cmd -pl engine compile exec:java `
  "-Dexec.mainClass=com.routeforge.engine.cli.RouteCli" `
  "-Dexec.args=--pbf data/liechtenstein-latest.osm.pbf --from 47.142,9.524 --to 47.166,9.510 --profile car --algo astar"
```

Pass `--algo dijkstra | astar | bidirectional` and
`--profile car | bike | foot` to compare. Output is JSON with route geometry,
distance, duration, and how many nodes the algorithm settled (useful for
comparing the algorithms head-to-head on the same query).

## License

TBD.
