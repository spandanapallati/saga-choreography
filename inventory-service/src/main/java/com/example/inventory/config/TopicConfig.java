package com.example.inventory.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Every service declares all saga topics; creation is idempotent. */
@Configuration
public class TopicConfig {

    @Bean NewTopic orderCreated()       { return topic("order-created"); }
    @Bean NewTopic paymentSucceeded()   { return topic("payment-succeeded"); }
    @Bean NewTopic paymentFailed()      { return topic("payment-failed"); }
    @Bean NewTopic inventoryReserved()  { return topic("inventory-reserved"); }
    @Bean NewTopic inventoryFailed()    { return topic("inventory-failed"); }
    @Bean NewTopic orderCancelled()     { return topic("order-cancelled"); }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
