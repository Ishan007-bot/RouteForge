package com.routeforge.api;

import com.routeforge.api.service.RouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Context loads" smoke test.
 * <p>
 * The {@code test} profile activates {@link com.routeforge.api.config.TestEngineConfig},
 * which builds a tiny in-memory graph — no PBF file needed.
 * If wiring is broken, this test fails fast with a clear error message.
 */
@SpringBootTest
@ActiveProfiles("test")
class RouteForgeApiApplicationTests {

    @Autowired RouteService service;

    @Test
    void contextLoads() {
        assertThat(service).isNotNull();
        assertThat(service.graph().nodeCount()).isGreaterThan(0);
    }
}
