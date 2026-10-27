package com.example.payment.job;

import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentRepository;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.domain.Wallet;
import com.example.payment.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Executes pending refunds with exponential backoff (10s, 20s, 40s, 80s) and escalates to
 * REFUND_FAILED after MAX_ATTEMPTS. If the process dies mid-refund the transaction rolls back,
 * the row is still REFUND_PENDING, and the next tick retries it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundJob {

    private static final int MAX_ATTEMPTS = 5;

    private final PaymentRepository payments;
    private final WalletRepository wallets;

    @Value("${saga.simulate-refund-failures:0}")
    private int simulateFailures;

    @Scheduled(fixedDelay = 5_000)
    @Transactional
    public void processRefunds() {
        for (Payment p : payments.lockDueRefunds(20)) {
            try {
                if (p.getRefundAttempts() < simulateFailures) {
                    throw new IllegalStateException("Simulated payment-gateway failure");
                }
                // Real system: paymentGateway.refund(p.getOrderId(), p.getAmount());  <- may throw

                Wallet w = wallets.findForUpdate(p.getUserId()).orElseThrow();
                w.setBalance(w.getBalance().add(p.getAmount()));
                p.setStatus(PaymentStatus.REFUNDED);
                log.info("Refunded order {} (amount {})", p.getOrderId(), p.getAmount());
            } catch (Exception ex) {
                int attempts = p.getRefundAttempts() + 1;
                p.setRefundAttempts(attempts);
                if (attempts >= MAX_ATTEMPTS) {
                    p.setStatus(PaymentStatus.REFUND_FAILED);
                    log.error("ALERT: refund for order {} failed after {} attempts - manual action needed",
                            p.getOrderId(), attempts);
                } else {
                    long delay = (long) Math.pow(2, attempts) * 5;
                    p.setNextRetryAt(Instant.now().plusSeconds(delay));
                    log.warn("Refund for order {} failed (attempt {}): {}. Retrying in {}s",
                            p.getOrderId(), attempts, ex.getMessage(), delay);
                }
            }
        }
    }
}
