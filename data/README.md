# Data directory

This folder holds OSM extracts and other big files we don't want in Git.
Everything except this README is gitignored.

## Get a small OSM extract to start

Download Liechtenstein (~1 MB) from Geofabrik:

  https://download.geofabrik.de/europe/liechtenstein.html

Save the `liechtenstein-latest.osm.pbf` file here, then from the project root:

```powershell
.\mvnw.cmd -pl engine compile exec:java `
    "-Dexec.mainClass=com.routeforge.engine.osm.OsmPbfLoader" `
    "-Dexec.args=data/liechtenstein-latest.osm.pbf"
```

Expected output: counts of nodes, ways, and relations.

## Larger extracts (later phases)

- Country PBFs: https://download.geofabrik.de/
- City extracts via BBBike: https://extract.bbbike.org/
