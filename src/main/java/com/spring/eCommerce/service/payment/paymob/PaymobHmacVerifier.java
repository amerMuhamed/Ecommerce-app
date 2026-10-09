package com.spring.eCommerce.service.payment.paymob;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Verifies Paymob transaction callbacks as documented in "HMAC Transaction Callback":
 * the values of the keys below, in this lexicographical order, are concatenated without delimiters and
 * signed with HMAC-SHA512 using the account's HMAC secret. The hex digest arrives in the {@code hmac}
 * query parameter of both the processed (POST, JSON body) and response (GET redirect) callbacks.
 */
@Component
public class PaymobHmacVerifier {

    static final List<String> SIGNED_FIELDS = List.of(
            "amount_cents",
            "created_at",
            "currency",
            "error_occured",
            "has_parent_transaction",
            "id",
            "integration_id",
            "is_3d_secure",
            "is_auth",
            "is_capture",
            "is_refunded",
            "is_standalone_payment",
            "is_voided",
            "order.id",
            "owner",
            "pending",
            "source_data.pan",
            "source_data.sub_type",
            "source_data.type",
            "success"
    );

    private static final String ALGORITHM = "HmacSHA512";
    private static final Pattern SHA512_HEX = Pattern.compile("^[0-9a-fA-F]{128}$");

    @SuppressWarnings("unchecked")
    private static Object resolve(Map<String, Object> root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * Booleans as lowercase {@code true}/{@code false} and integral numbers without a fraction, matching the
     * documented concatenation example. Missing/null values contribute an empty string.
     */
    private static String fieldToString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean bool) {
            return bool.toString();
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte || value instanceof BigInteger) {
            return value.toString();
        }
        if (value instanceof Number number) {
            BigDecimal decimal = new BigDecimal(number.toString());
            try {
                return decimal.toBigIntegerExact().toString();
            } catch (ArithmeticException notIntegral) {
                return decimal.toPlainString();
            }
        }
        if (value instanceof Map || value instanceof List) {
            // Signed fields are scalars; a structured value means the payload is not a valid callback.
            throw new IllegalArgumentException("Signed field must be a scalar value");
        }
        return value.toString();
    }

    /**
     * Verifies the transaction processed callback. {@code transaction} is the callback's {@code obj}.
     */
    public boolean verify(Map<String, Object> transaction, String hmacParam, String hmacSecret) {
        if (transaction == null) {
            return false;
        }
        return verify(field -> resolve(transaction, field), hmacParam, hmacSecret);
    }

    /**
     * Verifies the transaction response callback (customer redirect), whose values arrive as flat query
     * parameters ({@code source_data.pan} etc.). The Paymob order id is sent as {@code order} in the documented
     * example and listed as {@code order_id} in the key table, so both names are accepted.
     */
    public boolean verifyRedirect(Map<String, String> queryParams, String hmacParam, String hmacSecret) {
        if (queryParams == null) {
            return false;
        }
        return verify(field -> {
            if ("order.id".equals(field)) {
                String orderId = queryParams.get("order_id");
                return orderId != null ? orderId : queryParams.get("order");
            }
            return queryParams.get(field);
        }, hmacParam, hmacSecret);
    }

    private boolean verify(Function<String, Object> valueOf, String hmacParam, String hmacSecret) {
        if (hmacParam == null || hmacSecret == null || hmacSecret.isBlank()) {
            return false;
        }
        String received = hmacParam.trim();
        if (!SHA512_HEX.matcher(received).matches()) {
            return false;
        }
        try {
            String expected = computeHmac(concatenate(valueOf), hmacSecret);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            return false;
        }
    }

    String concatenate(Map<String, Object> transaction) {
        return concatenate(field -> resolve(transaction, field));
    }

    private String concatenate(Function<String, Object> valueOf) {
        StringBuilder concatenated = new StringBuilder();
        for (String field : SIGNED_FIELDS) {
            concatenated.append(fieldToString(valueOf.apply(field)));
        }
        return concatenated.toString();
    }

    String computeHmac(String concatenated, String hmacSecret) throws Exception {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
        byte[] digest = mac.doFinal(concatenated.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
