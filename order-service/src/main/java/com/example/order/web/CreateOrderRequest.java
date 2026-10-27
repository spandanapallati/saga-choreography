package com.example.order.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Request to create an order and start the saga")
public record CreateOrderRequest(
        @Schema(description = "Customer id. Seeded users: 1 (balance 1000), 2 (balance 50)", example = "1")
        Long userId,

        @Schema(description = "Product id. Seeded product: 100 (10 units in stock)", example = "100")
        Long productId,

        @Schema(description = "Number of units", example = "2")
        int quantity,

        @Schema(description = "Amount to charge", example = "200")
        BigDecimal amount) {}
