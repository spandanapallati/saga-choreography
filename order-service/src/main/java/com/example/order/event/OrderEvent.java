package com.example.order.event;

import java.math.BigDecimal;

/** Single event payload shared (copied) by all three services. */
public record OrderEvent(String orderId,
                         Long userId,
                         Long productId,
                         int quantity,
                         BigDecimal amount,
                         String reason) {

    public OrderEvent withReason(String newReason) {
        return new OrderEvent(orderId, userId, productId, quantity, amount, newReason);
    }
}
