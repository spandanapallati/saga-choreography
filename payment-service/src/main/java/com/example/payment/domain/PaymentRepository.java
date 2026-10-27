package com.example.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    @Query(value = """
            select * from payment
            where status = 'REFUND_PENDING' and next_retry_at <= now()
            order by next_retry_at
            limit :batch
            for update skip locked
            """, nativeQuery = true)
    List<Payment> lockDueRefunds(@Param("batch") int batch);
}
