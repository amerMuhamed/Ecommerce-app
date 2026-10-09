package com.spring.eCommerce.dto.payment;

import com.spring.eCommerce.entity.enums.PaymentStatus;

/**
 * Minimal, server-side view of a payment shown to a customer returning from the provider's checkout.
 */
public record PaymentReturnResponseDto(
        Long orderId,
        PaymentStatus paymentStatus
) {
}
