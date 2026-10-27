package com.example.order.job;

import com.example.order.domain.Order;
import com.example.order.domain.OrderRepository;
import com.example.order.domain.OrderStatus;
import com.example.order.event.OrderEvent;
import com.example.order.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Watchdog: in choreography nobody owns the whole flow, so if an event never arrives the saga just stops.
 * This job cancels orders stuck in CREATED and publishes order-cancelled so that Payment (refund)
 * and Inventory (release) compensate whatever they already did.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaTimeoutJob {

    private final OrderRepository orders;
    private final OutboxService outbox;

    @Value("${saga.order-timeout-seconds}")
    private long timeoutSeconds;

    @Scheduled(fixedDelay = 15_000)
    @Transactional
    public void cancelStuckOrders() {
        Instant cutoff = Instant.now().minusSeconds(timeoutSeconds);
        for (Order o : orders.lockStuck(cutoff, 50)) {
            o.setStatus(OrderStatus.CANCELLED);
            o.setFailureReason("Saga timeout");
            outbox.publish("order-cancelled", o.getId(),
                    new OrderEvent(o.getId(), o.getUserId(), o.getProductId(), o.getQuantity(), o.getAmount(), "Saga timeout"));
            log.warn("Order {} timed out after {}s -> CANCELLED, compensation requested", o.getId(), timeoutSeconds);
        }
    }
}
