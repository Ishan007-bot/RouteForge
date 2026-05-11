package com.routeforge.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeforge.api.dto.RouteRequestDto;
import com.routeforge.api.dto.RouteResponseDto;
import com.routeforge.api.service.RouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link RouteController}.
 * <p>
 * {@code @WebMvcTest} loads only the web layer (controllers, exception handlers,
 * JSON converters). The service is mocked so this test doesn't need a graph
 * loaded — fast and focused on HTTP semantics.
 */
@WebMvcTest(RouteController.class)
class RouteControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean  RouteService service;

    @Test
    void validRequest_returnsRoute() throws Exception {
        when(service.route(any())).thenReturn(new RouteResponseDto(
                true, "astar", "car",
                1234.5, 60.0, 3L, 42L,
                List.of(new double[]{48.0, 11.0}, new double[]{48.001, 11.0})
        ));

        var req = new RouteRequestDto(48.0, 11.0, 48.001, 11.0, "car", "astar");
        mvc.perform(post("/api/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.algorithm").value("astar"))
                .andExpect(jsonPath("$.distanceMeters").value(1234.5))
                .andExpect(jsonPath("$.geometry").isArray())
                .andExpect(jsonPath("$.geometry.length()").value(2));
    }

    @Test
    void missingLatitude_returns400ValidationError() throws Exception {
        // Build a JSON body with fromLat omitted entirely.
        String body = """
                { "fromLon": 11.0, "toLat": 48.001, "toLon": 11.0 }
                """;
        mvc.perform(post("/api/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void outOfRangeLatitude_returns400() throws Exception {
        var req = new RouteRequestDto(95.0, 11.0, 48.001, 11.0, "car", "astar");
        mvc.perform(post("/api/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
