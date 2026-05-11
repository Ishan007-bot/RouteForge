# RouteForge

A route planner and traffic simulator. Built in Java (engine + API) with a React + MapLibre frontend.
Engine is later ported to Rust behind the same API for a ~2-3x speedup.

## Status

Phase 3 — Backend API. Spring Boot service over the engine: REST endpoints for
routing, isochrones, and metadata; in-memory route cache; Swagger UI; full
MockMvc + end-to-end test coverage.

Previous phases:
- Phase 0: Multi-module Maven scaffold + OSM PBF loader.
- Phase 1: CSR graph, car/bike/foot profiles, Dijkstra + A* + Bidirectional with indexed heap.
- Phase 2: Contraction Hierarchies (sub-millisecond queries on large graphs).

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

Pass `--algo dijkstra | astar | bidirectional | ch` and
`--profile car | bike | foot` to compare. Output is JSON with route geometry,
distance, duration, and how many nodes the algorithm settled (useful for
comparing the algorithms head-to-head on the same query).

## Run the API (Phase 3)

```powershell
.\mvnw.cmd -pl api spring-boot:run `
  "-Dspring-boot.run.arguments=--routeforge.pbf-file=data/liechtenstein-latest.osm.pbf"
```

The service comes up on `http://localhost:8080`. Useful endpoints:

| Endpoint | What it does |
|---|---|
| `GET  /api/info`         | Engine and graph summary. |
| `POST /api/route`        | Plan a route between two coordinates. |
| `POST /api/isochrone`    | Area reachable from a point within a time budget. |
| `GET  /actuator/health`  | Health probe. |
| `GET  /swagger-ui.html`  | Interactive API docs (try-it-out). |

Quick `curl`:

```bash
curl -X POST http://localhost:8080/api/route \
  -H "Content-Type: application/json" \
  -d '{"fromLat":47.142,"fromLon":9.524,"toLat":47.166,"toLon":9.510,"profile":"car","algo":"astar"}'
```

## License

TBD.
