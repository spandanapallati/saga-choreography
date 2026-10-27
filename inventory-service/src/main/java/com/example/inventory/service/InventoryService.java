package com.example.inventory.service;

import com.example.inventory.domain.*;
import com.example.inventory.event.OrderEvent;
import com.example.inventory.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final StockRepository stocks;
    private final ReservationRepository reservations;
    private final OutboxService outbox;

    /** Saga step 2: reserve stock (local transaction). */
    @KafkaListener(topics = "payment-succeeded")
    @Transactional
    public void onPaymentSucceeded(OrderEvent e) {
        if (reservations.existsById(e.orderId())) {      // duplicate delivery OR a tombstone from order-cancelled
            log.info("Order {} already known to Inventory, skipping", e.orderId());
            return;
        }

        Reservation r = new Reservation();
        r.setOrderId(e.orderId());
        r.setProductId(e.productId());
        r.setQuantity(e.quantity());

        Stock s = stocks.findForUpdate(e.productId()).orElse(null);
        if (s == null || s.getAvailable() < e.quantity()) {
            r.setStatus(ReservationStatus.FAILED);       // record the outcome so redeliveries don't re-decide
            reservations.save(r);
            outbox.publish("inventory-failed", e.orderId(), e.withReason("Out of stock"));
            log.info("Reservation FAILED for order {} (out of stock)", e.orderId());
            return;
        }

        s.setAvailable(s.getAvailable() - e.quantity());
        r.setStatus(ReservationStatus.RESERVED);
        reservations.save(r);
        outbox.publish("inventory-reserved", e.orderId(), e);
        log.info("Reserved {} x product {} for order {}", e.quantity(), e.productId(), e.orderId());
    }

    /** Compensation for a timed-out order: release stock if it was reserved. */
    @KafkaListener(topics = "order-cancelled")
    @Transactional
    public void onOrderCancelled(OrderEvent e) {
        Reservation r = reservations.findById(e.orderId()).orElse(null);

        if (r == null) {                                 // cancelled before payment-succeeded arrived
            r = new Reservation();                       // tombstone
            r.setOrderId(e.orderId());
            r.setProductId(e.productId());
            r.setQuantity(e.quantity());
            r.setStatus(ReservationStatus.CANCELLED);
            reservations.save(r);
            log.info("Tombstone saved for order {}", e.orderId());
            return;
        }

        if (r.getStatus() == ReservationStatus.RESERVED) {
            Stock s = stocks.findForUpdate(r.getProductId()).orElseThrow();
            s.setAvailable(s.getAvailable() + r.getQuantity());
            r.setStatus(ReservationStatus.RELEASED);
            log.info("Released {} x product {} for cancelled order {}", r.getQuantity(), r.getProductId(), e.orderId());
        }
        // FAILED / RELEASED / CANCELLED -> nothing to do (idempotent)
    }
}
