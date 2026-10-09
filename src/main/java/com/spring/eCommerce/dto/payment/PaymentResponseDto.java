package com.spring.eCommerce.dto.payment;

import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public record PaymentResponseDto(
        Long id,
        Long orderId,
        BigDecimal amount,
        String currency,
        PaymentProvider provider,
        PaymentStatus status,
        String providerPaymentId,
        String checkoutUrl,
        String idempotencyKey,
        List<PaymentAttemptResponseDto> attempts,
        Date createdDate,
        Date modifiedDate
) {
}
