package com.example.order.web;

import com.example.order.domain.Order;
import com.example.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Create orders (starts the saga) and follow their status")
public class OrderController {

    private final OrderService service;

    @Operation(
            summary = "Create an order and start the saga",
            description = "Returns 201 immediately with status CREATED. The saga runs asynchronously over Kafka; "
                    + "poll GET /orders/{id} to see COMPLETED or CANCELLED.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "1. Happy path -> COMPLETED",
                                    value = "{\"userId\":1,\"productId\":100,\"quantity\":2,\"amount\":200}"),
                            @ExampleObject(name = "2. Payment fails (user 2 has only 50) -> CANCELLED",
                                    value = "{\"userId\":2,\"productId\":100,\"quantity\":1,\"amount\":200}"),
                            @ExampleObject(name = "3. Out of stock -> refund (compensation) -> CANCELLED",
                                    value = "{\"userId\":1,\"productId\":100,\"quantity\":50,\"amount\":100}")
                    })),
            responses = @ApiResponse(responseCode = "201", description = "Order accepted, saga started"))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(@RequestBody CreateOrderRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Get an order by id",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order found"),
                    @ApiResponse(responseCode = "404", description = "Order not found", content = @Content)
            })
    @GetMapping("/{id}")
    public Order get(@Parameter(description = "Order id returned by POST /orders") @PathVariable String id) {
        return service.get(id);
    }

    @Operation(summary = "List all orders")
    @GetMapping
    public List<Order> list() {
        return service.list();
    }
}
