package com.spring.eCommerce.dto.payment;

import com.spring.eCommerce.entity.enums.PaymentProvider;

/**
 * @param billingPhoneNumber customer phone forwarded to the provider's checkout (required by Paymob); not persisted
 * @param billingEmail       customer email for the provider's checkout when the username is not an email; not persisted
 */
public record PaymentInitiateRequest(
        PaymentProvider provider,
        String idempotencyKey,
        String billingPhoneNumber,
        String billingEmail
) {
    public PaymentInitiateRequest(PaymentProvider provider, String idempotencyKey) {
        this(provider, idempotencyKey, null, null);
    }
}
