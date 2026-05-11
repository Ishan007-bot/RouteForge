package com.routeforge.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Spring Boot entry point.
 * <p>
 * Run with:
 * <pre>
 *   .\mvnw.cmd -pl api spring-boot:run \
 *       "-Dspring-boot.run.arguments=--routeforge.pbf-file=data/liechtenstein-latest.osm.pbf"
 * </pre>
 * or after {@code mvn package}, with the executable jar:
 * <pre>
 *   java -jar api/target/routeforge-api-0.1.0-SNAPSHOT.jar \
 *       --routeforge.pbf-file=data/liechtenstein-latest.osm.pbf
 * </pre>
 *
 * <h3>Default endpoints</h3>
 * <ul>
 *   <li>{@code GET  /api/info}        — engine + graph summary</li>
 *   <li>{@code GET  /actuator/health} — Spring Boot health probe</li>
 *   <li>{@code POST /api/route}       — plan a route between two coordinates</li>
 *   <li>{@code POST /api/isochrone}   — area reachable from a point within a budget</li>
 *   <li>{@code GET  /swagger-ui.html} — interactive API docs</li>
 * </ul>
 */
@SpringBootApplication
@EnableCaching
public class RouteForgeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RouteForgeApiApplication.class, args);
    }
}
