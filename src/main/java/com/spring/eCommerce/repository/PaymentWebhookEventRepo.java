package com.spring.eCommerce.repository;

import com.spring.eCommerce.entity.PaymentWebhookEvent;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentWebhookEventRepo extends JpaRepository<PaymentWebhookEvent, Long> {
    boolean existsByProviderAndEventId(PaymentProvider provider, String eventId);

    Optional<PaymentWebhookEvent> findByProviderAndEventId(PaymentProvider provider, String eventId);
}
