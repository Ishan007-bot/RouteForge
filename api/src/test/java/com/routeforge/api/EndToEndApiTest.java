package com.routeforge.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeforge.api.dto.IsochroneRequestDto;
import com.routeforge.api.dto.RouteRequestDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack test against the in-memory triangle graph.
 * <p>
 * Exercises real Spring wiring + real engine + real controllers. Slow-ish
 * (full Spring context boot) but worth one per Phase to catch integration issues
 * the slice tests can't.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndToEndApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void info_returnsGraphMetadata() throws Exception {
        mvc.perform(get("/api/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("routeforge-api"))
                .andExpect(jsonPath("$.graph.nodes").value(3));
    }

    @Test
    void route_findsPathOnTriangleGraph() throws Exception {
        // The test graph has nodes at (48.0, 11.0), (48.0010, 11.0), (48.0005, 11.0010).
        var req = new RouteRequestDto(48.0, 11.0, 48.0010, 11.0, "car", "astar");
        mvc.perform(post("/api/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.distanceMeters").value(100.0))
                .andExpect(jsonPath("$.geometry.length()").value(2));
    }

    @Test
    void isochrone_reachesNearbyNodes() throws Exception {
        // 60s budget is enough to cover the 100m direct edge at car speeds.
        var req = new IsochroneRequestDto(48.0, 11.0, 600.0, "car");
        mvc.perform(post("/api/isochrone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").value("car"))
                .andExpect(jsonPath("$.nodeCount").value(3)); // all three nodes reachable
    }
}
