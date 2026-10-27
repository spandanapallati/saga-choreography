package com.example.order.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository repo;
    private final ObjectMapper mapper;

    /**
     * MANDATORY: must be called inside the caller's business transaction, so the
     * business change and the outgoing event commit (or roll back) together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, String key, Object event) {
        OutboxEvent o = new OutboxEvent();
        o.setTopic(topic);
        o.setMessageKey(key);
        try {
            o.setPayload(mapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event for topic " + topic, e);
        }
        o.setStatus("PENDING");
        o.setCreatedAt(Instant.now());
        repo.save(o);
    }
}
