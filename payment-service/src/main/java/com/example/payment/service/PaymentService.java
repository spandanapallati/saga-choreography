package com.example.payment.service;

import com.example.payment.domain.*;
import com.example.payment.event.OrderEvent;
import com.example.payment.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final WalletRepository wallets;
    private final PaymentRepository payments;
    private final OutboxService outbox;

    @Value("${saga.compensation-delay-ms:0}")
    private long compensationDelayMs;

    /** Saga step 1: charge the customer (local transaction). */
    @KafkaListener(topics = "order-created")
    @Transactional
    public void onOrderCreated(OrderEvent e) {
        if (payments.existsById(e.orderId())) {          // duplicate delivery OR a tombstone from order-cancelled
            log.info("Order {} already known to Payment, skipping", e.orderId());
            return;
        }

        Payment p = new Payment();
        p.setOrderId(e.orderId());
        p.setUserId(e.userId());
        p.setAmount(e.amount());

        Wallet w = wallets.findForUpdate(e.userId()).orElse(null);
        if (w == null || w.getBalance().compareTo(e.amount()) < 0) {
            p.setStatus(PaymentStatus.FAILED);
            payments.save(p);
            outbox.publish("payment-failed", e.orderId(), e.withReason("Insufficient balance"));
            log.info("Payment FAILED for order {}", e.orderId());
            return;
        }

        w.setBalance(w.getBalance().subtract(e.amount()));
        p.setStatus(PaymentStatus.COMPLETED);
        payments.save(p);
        outbox.publish("payment-succeeded", e.orderId(), e);
        log.info("Payment COMPLETED for order {} (amount {})", e.orderId(), e.amount());
    }

    /**
     * Compensation triggers: inventory-failed (business failure) and order-cancelled (saga timeout).
     * This handler only records the INTENT to refund (REFUND_PENDING); RefundJob performs the refund
     * with retries, so a crash or gateway failure can never lose the compensation.
     */
    @KafkaListener(topics = {"inventory-failed", "order-cancelled"})
    @Transactional
    public void onCompensationRequested(OrderEvent e) throws InterruptedException {
        log.info("Compensation requested for order {} ({})", e.orderId(), e.reason());
        if (compensationDelayMs > 0) {                   // demo only: gives you time to kill -9 the process
            log.warn("DEMO: sleeping {} ms inside compensation handler", compensationDelayMs);
            Thread.sleep(compensationDelayMs);
        }

        Payment p = payments.findById(e.orderId()).orElse(null);

        if (p == null) {                                 // cancellation arrived BEFORE the payment was processed
            p = new Payment();                           // leave a tombstone so a late order-created is ignored
            p.setOrderId(e.orderId());
            p.setUserId(e.userId());
            p.setAmount(e.amount());
            p.setStatus(PaymentStatus.CANCELLED);
            payments.save(p);
            log.info("Tombstone saved for order {}", e.orderId());
            return;
        }

        if (p.getStatus() == PaymentStatus.COMPLETED) {
            p.setStatus(PaymentStatus.REFUND_PENDING);   // durable intent
            p.setNextRetryAt(Instant.now());
            log.info("Order {} marked REFUND_PENDING", e.orderId());
        }
        // FAILED / CANCELLED / REFUND_PENDING / REFUNDED / REFUND_FAILED -> nothing to do (idempotent)
    }
}
