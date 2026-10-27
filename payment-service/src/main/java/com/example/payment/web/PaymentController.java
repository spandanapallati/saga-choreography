package com.example.payment.web;

import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentRepository;
import com.example.payment.domain.Wallet;
import com.example.payment.domain.WalletRepository;
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
@Tag(name = "Payments", description = "Read-only views of wallets and payments (state is changed only by Kafka events)")
public class PaymentController {

    private final WalletRepository wallets;
    private final PaymentRepository payments;

    @Operation(summary = "Get a wallet balance",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Wallet found"),
                    @ApiResponse(responseCode = "404", description = "Unknown user", content = @Content)
            })
    @GetMapping("/wallets/{userId}")
    public ResponseEntity<Wallet> wallet(@Parameter(description = "Seeded users: 1, 2", example = "1")
                                         @PathVariable Long userId) {
        return wallets.findById(userId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get the payment for an order",
            description = "Status: COMPLETED, FAILED, CANCELLED, REFUND_PENDING, REFUNDED or REFUND_FAILED",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Payment found"),
                    @ApiResponse(responseCode = "404", description = "No payment for this order", content = @Content)
            })
    @GetMapping("/payments/{orderId}")
    public ResponseEntity<Payment> payment(@Parameter(description = "Order id") @PathVariable String orderId) {
        return payments.findById(orderId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "List all payments")
    @GetMapping("/payments")
    public List<Payment> all() {
        return payments.findAll();
    }
}
