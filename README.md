# RouteForge

A route planner and traffic simulator. Built in Java (engine + API) with a React + MapLibre frontend.
Engine is later ported to Rust behind the same API for a ~2-3x speedup.

## Status

Phase 0 — Foundations. The project is being built up phase by phase. See `docs/` (later) for the full plan.

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

## Try the OSM loader

Download a small OSM extract (e.g. Liechtenstein, ~1 MB):

- https://download.geofabrik.de/europe/liechtenstein.html

Save it to `data/liechtenstein-latest.osm.pbf`, then:

```powershell
.\mvnw.cmd -pl engine compile exec:java "-Dexec.mainClass=com.routeforge.engine.osm.OsmPbfLoader" "-Dexec.args=data/liechtenstein-latest.osm.pbf"
```

You should see counts of nodes, ways, and relations.

## License

TBD.
