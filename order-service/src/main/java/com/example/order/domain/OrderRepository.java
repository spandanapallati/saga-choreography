package com.example.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String> {

    /** Orders stuck in CREATED since before the cutoff. SKIP LOCKED = safe with multiple instances. */
    @Query(value = """
            select * from orders
            where status = 'CREATED' and created_at < :before
            order by created_at
            limit :batch
            for update skip locked
            """, nativeQuery = true)
    List<Order> lockStuck(@Param("before") Instant before, @Param("batch") int batch);
}
