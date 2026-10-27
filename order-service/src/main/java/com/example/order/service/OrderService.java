package com.example.order.service;

import com.example.order.domain.Order;
import com.example.order.domain.OrderRepository;
import com.example.order.domain.OrderStatus;
import com.example.order.event.OrderEvent;
import com.example.order.outbox.OutboxService;
import com.example.order.web.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repo;
    private final OutboxService outbox;

    /** Saga step 0: save the order and (atomically) queue the order-created event. */
    @Transactional
    public Order create(CreateOrderRequest r) {
        Order o = new Order();
        o.setId(UUID.randomUUID().toString());
        o.setUserId(r.userId());
        o.setProductId(r.productId());
        o.setQuantity(r.quantity());
        o.setAmount(r.amount());
        o.setStatus(OrderStatus.CREATED);
        repo.save(o);

        outbox.publish("order-created", o.getId(),
                new OrderEvent(o.getId(), o.getUserId(), o.getProductId(), o.getQuantity(), o.getAmount(), null));
        log.info("Order {} created", o.getId());
        return o;
    }

    @Transactional(readOnly = true)
    public Order get(String id) {
        return repo.findById(id).orElseThrow(() -> new NoSuchElementException("Order not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Order> list() {
        return repo.findAll();
    }

    // ---- saga outcomes ----

    @KafkaListener(topics = "inventory-reserved")
    @Transactional
    public void onInventoryReserved(OrderEvent e) {
        update(e.orderId(), OrderStatus.COMPLETED, null);
    }

    @KafkaListener(topics = "payment-failed")
    @Transactional
    public void onPaymentFailed(OrderEvent e) {
        update(e.orderId(), OrderStatus.CANCELLED, e.reason());
    }

    @KafkaListener(topics = "inventory-failed")
    @Transactional
    public void onInventoryFailed(OrderEvent e) {
        update(e.orderId(), OrderStatus.CANCELLED, e.reason());
    }

    private void update(String orderId, OrderStatus status, String reason) {
        Order o = repo.findById(orderId).orElse(null);
        if (o == null) {
            log.warn("Event for unknown order {}", orderId);
            return;
        }
        if (o.getStatus() != OrderStatus.CREATED) {      // idempotent: duplicates / late events are ignored
            log.info("Order {} already {}, ignoring {}", orderId, o.getStatus(), status);
            return;
        }
        o.setStatus(status);
        o.setFailureReason(reason);
        log.info("Order {} -> {}{}", orderId, status, reason == null ? "" : " (" + reason + ")");
    }
}
