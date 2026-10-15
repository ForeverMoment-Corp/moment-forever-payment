package com.forvmom.MomentForeverPayment.repository;

import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {
    Optional<Payment> findByProviderSessionId(String providerSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.providerSessionId = :providerSessionId")
    Optional<Payment> findByProviderSessionIdForUpdate(
            @Param("providerSessionId") String providerSessionId);

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findFirstByBookingIdOrderByIdDesc(String bookingId);

    Optional<Payment> findFirstByCorrelationIdOrderByIdDesc(String correlationId);

    // ─── Read/query APIs ─────────────────────────────────────────────
    // Derived finders (no @Query with nullable guards — same 42P18 reason
    // as booking-service: null binds break PostgreSQL type resolution).

    List<Payment> findAllByBookingIdOrderByIdDesc(String bookingId);

    Page<Payment> findAllByBookingId(String bookingId, Pageable pageable);

    Page<Payment> findAllByStatus(PaymentStatus status, Pageable pageable);

    boolean existsByBookingId(String bookingId);
}
