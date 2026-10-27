package com.example.payment.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /** SKIP LOCKED lets several instances run the relay without claiming the same rows. */
    @Query(value = """
            select * from outbox
            where status = 'PENDING'
            order by id
            limit :batch
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("batch") int batch);

    @Modifying
    @Query("delete from OutboxEvent o where o.status = 'SENT' and o.sentAt < :before")
    int deleteSentBefore(@Param("before") Instant before);
}
