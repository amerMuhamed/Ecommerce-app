package com.spring.eCommerce.dto.payment;

import com.spring.eCommerce.entity.enums.PaymentAttemptStatus;
import com.spring.eCommerce.entity.enums.PaymentProvider;

import java.util.Date;

public record PaymentAttemptResponseDto(
        Long id,
        Integer attemptNumber,
        PaymentProvider provider,
        PaymentAttemptStatus status,
        String providerReference,
        Date createdDate
) {
}
