package com.forvmom.MomentForeverPayment.repository;

import com.forvmom.MomentForeverPayment.domain.entity.WebhookInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookInboxRepository extends JpaRepository<WebhookInbox, Long> {
    boolean existsByEventId(String eventId);
}
