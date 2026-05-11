package com.routeforge.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeforge.api.dto.IsochroneRequestDto;
import com.routeforge.api.dto.IsochroneResponseDto;
import com.routeforge.api.service.RouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IsochroneController.class)
class IsochroneControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean  RouteService service;

    @Test
    void validRequest_returnsPoints() throws Exception {
        when(service.isochrone(anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenReturn(new IsochroneResponseDto("car", 600.0, 5L, 2,
                        List.of(new double[]{48.0, 11.0, 0.0},
                                new double[]{48.001, 11.0, 60.0})));

        var req = new IsochroneRequestDto(48.0, 11.0, 600.0, "car");
        mvc.perform(post("/api/isochrone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").value("car"))
                .andExpect(jsonPath("$.nodeCount").value(2))
                .andExpect(jsonPath("$.points").isArray());
    }

    @Test
    void negativeBudget_isRejected() throws Exception {
        var req = new IsochroneRequestDto(48.0, 11.0, -10.0, "car");
        mvc.perform(post("/api/isochrone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
