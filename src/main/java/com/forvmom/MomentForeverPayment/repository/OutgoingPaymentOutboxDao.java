package com.forvmom.MomentForeverPayment.repository;

import com.forvmom.MomentForeverPayment.domain.entity.OutgoingPaymentOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

public interface OutgoingPaymentOutboxDao extends JpaRepository<OutgoingPaymentOutbox, Long> {

    List<OutgoingPaymentOutbox> findByStatusInAndUpdatedAtBefore(List<String> statuses, LocalDateTime cutoff);

    Optional<OutgoingPaymentOutbox> findByProducerAndEventId(String producer, String eventId);

    Optional<OutgoingPaymentOutbox> findFirstByBookingIdAndEventTypeOrderByCreatedAtDesc(
            String bookingId, String eventType);

    @Modifying
    @Query("DELETE FROM OutgoingPaymentOutbox o WHERE o.status = :status AND o.updatedAt < :cutoff")
    int deleteByStatusAndUpdatedAtBefore(@Param("status") String status,
                                         @Param("cutoff") LocalDateTime cutoff);

    Page<OutgoingPaymentOutbox> findByStatus(String status, Pageable pageable);

}