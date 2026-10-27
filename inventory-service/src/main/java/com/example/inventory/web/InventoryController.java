package com.example.inventory.web;

import com.example.inventory.domain.Reservation;
import com.example.inventory.domain.ReservationRepository;
import com.example.inventory.domain.Stock;
import com.example.inventory.domain.StockRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only endpoints to inspect state while testing. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Read-only views of stock and reservations (state is changed only by Kafka events)")
public class InventoryController {

    private final StockRepository stocks;
    private final ReservationRepository reservations;

    @Operation(summary = "Get available stock for a product",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Stock found"),
                    @ApiResponse(responseCode = "404", description = "Unknown product", content = @Content)
            })
    @GetMapping("/stock/{productId}")
    public ResponseEntity<Stock> stock(@Parameter(description = "Seeded product: 100", example = "100")
                                       @PathVariable Long productId) {
        return stocks.findById(productId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get the reservation for an order",
            description = "Status: RESERVED, FAILED, RELEASED or CANCELLED",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Reservation found"),
                    @ApiResponse(responseCode = "404", description = "No reservation for this order", content = @Content)
            })
    @GetMapping("/reservations/{orderId}")
    public ResponseEntity<Reservation> reservation(@Parameter(description = "Order id") @PathVariable String orderId) {
        return reservations.findById(orderId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "List all reservations")
    @GetMapping("/reservations")
    public List<Reservation> all() {
        return reservations.findAll();
    }
}
