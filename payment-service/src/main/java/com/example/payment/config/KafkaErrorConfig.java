package com.example.payment.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/** Retry with exponential backoff (1s,2s,4s,8s,16s), then park the message on <topic>.DLT. */
@Configuration
public class KafkaErrorConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(5);
        backOff.setInitialInterval(1_000);
        backOff.setMultiplier(2);
        backOff.setMaxInterval(30_000);

        return new DefaultErrorHandler((rec, ex) -> {
            try {
                String key = rec.key() == null ? null : rec.key().toString();
                kafka.send(rec.topic() + ".DLT", key, mapper.writeValueAsString(rec.value()));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }, backOff);
    }
}
