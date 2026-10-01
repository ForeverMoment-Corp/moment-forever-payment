package com.forvmom.MomentForeverPayment.repository;

import com.forvmom.MomentForeverPayment.domain.entity.PaymentOutbox;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentOutboxDao extends JpaRepository<PaymentOutbox, Long> {

    Optional<PaymentOutbox> findByProducerAndEventId(String producer, String eventId);

    List<PaymentOutbox> findByStatusInAndUpdatedAtBefore(List<String> statuses, LocalDateTime cutoff);

    @Modifying
    @Query("DELETE FROM PaymentOutbox p WHERE p.status = :status AND p.updatedAt < :cutoff")
    int deleteByStatusAndUpdatedAtBefore(@Param("status") String status,
                                         @Param("cutoff") LocalDateTime cutoff);

    Page<PaymentOutbox> findByStatus(String status, Pageable pageable);
}