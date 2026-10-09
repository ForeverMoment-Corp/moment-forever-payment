package com.forvmom.MomentForeverPayment.repository;

import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByProviderSessionId(String providerSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.providerSessionId = :providerSessionId")
    Optional<Payment> findByProviderSessionIdForUpdate(
            @Param("providerSessionId") String providerSessionId);

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findFirstByBookingIdOrderByIdDesc(String bookingId);
}
