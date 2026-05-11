package com.routeforge.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI routeForgeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RouteForge API")
                        .description("Route planning and isochrones over an OpenStreetMap road graph.")
                        .version("0.1.0")
                        .contact(new Contact().name("RouteForge").url("https://github.com/Ishan007-bot/RouteForge"))
                        .license(new License().name("TBD")));
    }
}
