package com.example.inventory.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/** Polls the outbox table and publishes pending rows to Kafka (at-least-once). */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxRepository repo;
    private final KafkaTemplate<String, String> kafka;

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void relay() {
        for (OutboxEvent e : repo.lockNextBatch(100)) {
            try {
                kafka.send(e.getTopic(), e.getMessageKey(), e.getPayload()).get(5, TimeUnit.SECONDS);
                e.setStatus("SENT");
                e.setSentAt(Instant.now());
                log.info("Outbox -> Kafka topic={} key={}", e.getTopic(), e.getMessageKey());
            } catch (Exception ex) {
                e.setAttempts(e.getAttempts() + 1);
                log.warn("Outbox publish failed id={} attempt={}: {}", e.getId(), e.getAttempts(), ex.toString());
                break; // stop here to keep ordering; retried on the next tick
            }
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanup() {
        int deleted = repo.deleteSentBefore(Instant.now().minus(7, ChronoUnit.DAYS));
        if (deleted > 0) log.info("Outbox cleanup removed {} rows", deleted);
    }
}
