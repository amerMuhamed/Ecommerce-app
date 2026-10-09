package com.spring.eCommerce.service.payment.paymob;

import com.spring.eCommerce.exception.BusinessException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/**
 * Converts major-unit amounts to the minor units ("cents") Paymob expects.
 */
public final class PaymobMoney {

    /**
     * Currencies this application charges in, with their ISO 4217 minor-unit digits.
     * EGP is the only currency the application uses (Payment.currency defaults to EGP).
     * Add a currency here only after confirming its minor unit and that the Paymob integration supports it.
     */
    private static final Map<String, Integer> SUPPORTED_FRACTION_DIGITS = Map.of("EGP", 2);

    private PaymobMoney() {
    }

    public static long toMinorUnits(BigDecimal majorAmount, String currency) {
        int fractionDigits = fractionDigits(currency);
        if (majorAmount == null) {
            throw new BusinessException("Payment amount is missing.");
        }
        if (majorAmount.signum() <= 0) {
            throw new BusinessException("Payment amount must be greater than zero.");
        }
        try {
            return majorAmount.movePointRight(fractionDigits).longValueExact();
        } catch (ArithmeticException ex) {
            throw new BusinessException("Payment amount has more precision than the currency supports.");
        }
    }

    public static boolean isSupported(String currency) {
        return currency != null && SUPPORTED_FRACTION_DIGITS.containsKey(currency.toUpperCase(Locale.ROOT));
    }

    private static int fractionDigits(String currency) {
        if (!isSupported(currency)) {
            throw new BusinessException("Unsupported payment currency: " + currency);
        }
        return SUPPORTED_FRACTION_DIGITS.get(currency.toUpperCase(Locale.ROOT));
    }
}
