package com.spring.eCommerce.repository;

import com.spring.eCommerce.entity.PaymentAttempt;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentAttemptRepo extends JpaRepository<PaymentAttempt, Long> {
    List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberDesc(Long paymentId);

    long countByPaymentId(Long paymentId);

    /**
     * Payments that ever received this provider reference, including references replaced by a later attempt.
     */
    @Query("select distinct a.payment.id from PaymentAttempt a "
            + "where a.provider = :provider and a.providerReference = :providerReference")
    List<Long> findPaymentIdsByProviderReference(@Param("provider") PaymentProvider provider,
                                                 @Param("providerReference") String providerReference);
}
