package com.example.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("SAGA Choreography - Payment Service")
                .description("Charges the wallet and compensates with a retried refund. Read-only endpoints for inspecting state.")
                .version("1.0.0"));
    }
}
