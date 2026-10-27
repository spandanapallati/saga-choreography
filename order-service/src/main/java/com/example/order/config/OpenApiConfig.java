package com.example.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("SAGA Choreography - Order Service")
                .description("Starts the saga (POST /orders), tracks the final outcome and cancels orders that time out.")
                .version("1.0.0"));
    }
}
