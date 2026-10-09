package com.spring.eCommerce.repository;

import com.spring.eCommerce.entity.Payment;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepo extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    Optional<Payment> findByProviderAndProviderPaymentId(PaymentProvider provider, String providerPaymentId);

    /**
     * Row lock so concurrent webhook deliveries for the same payment are applied one at a time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    List<Payment> findByPayerId(Long payerId);
}
