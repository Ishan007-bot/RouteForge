// Per-profile color so the route line, primary pin, and chips all match.
// Drawn from the cartographic palette: brass (gold), lake (teal), oxblood (red).

export function profileColor(profile: string): string {
  switch (profile) {
    case "car":  return "#c9a35a"; // brass
    case "bike": return "#5fa8a4"; // lake teal
    case "foot": return "#c8553d"; // oxblood
    default:     return "#c9a35a";
  }
}

/** Slightly darker companion for line glow / pin shaft. */
export function profileColorDeep(profile: string): string {
  switch (profile) {
    case "car":  return "#7c632f";
    case "bike": return "#326967";
    case "foot": return "#7d3024";
    default:     return "#7c632f";
  }
}

export function profileLabel(profile: string): string {
  switch (profile) {
    case "car":  return "Driving";
    case "bike": return "Cycling";
    case "foot": return "On foot";
    default:     return profile;
  }
}

export function algoLabel(algo: string): string {
  switch (algo) {
    case "dijkstra":      return "Dijkstra";
    case "astar":         return "A★";
    case "bidirectional": return "Bidirectional";
    case "ch":            return "Contraction Hierarchies";
    default:              return algo;
  }
}

export function algoShort(algo: string): string {
  switch (algo) {
    case "dijkstra":      return "Dijkstra";
    case "astar":         return "A★";
    case "bidirectional": return "Bi-Dir";
    case "ch":            return "CH";
    default:              return algo;
  }
}
