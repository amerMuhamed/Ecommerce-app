package com.spring.eCommerce.entity.enums;

import java.util.Locale;
import java.util.Optional;

public enum PaymentProvider {
    PAYMOB,
    STRIPE,
    MOCK;

    /**
     * Resolves provider names used in URLs (e.g. "paymob") case-insensitively.
     */
    public static Optional<PaymentProvider> fromPathSegment(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
