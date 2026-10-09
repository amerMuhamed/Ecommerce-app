package com.spring.eCommerce.service.payment.paymob;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaymobHmacVerifierTest {

    private static final String SECRET = "test-hmac-secret";
    private final PaymobHmacVerifier verifier = new PaymobHmacVerifier();

    static String hmacOf(String concatenated) {
        return hmacOf(concatenated, SECRET);
    }

    static String hmacOf(String concatenated, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(concatenated.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void acceptsAuthenticCallback() {
        Map<String, Object> transaction = transaction();
        String hmac = hmacOf(expectedConcatenated());
        assertTrue(verifier.verify(transaction, hmac, SECRET));
    }

    @Test
    void acceptsUppercaseHexSignature() {
        String hmac = hmacOf(expectedConcatenated()).toUpperCase(Locale.ROOT);
        assertTrue(verifier.verify(transaction(), hmac, SECRET));
    }

    @Test
    void rejectsTamperedCallback() {
        Map<String, Object> transaction = transaction();
        String hmac = hmacOf(expectedConcatenated());
        transaction.put("success", false);
        assertFalse(verifier.verify(transaction, hmac, SECRET));
    }

    @Test
    void rejectsModifiedAmount() {
        Map<String, Object> transaction = transaction();
        String hmac = hmacOf(expectedConcatenated());
        transaction.put("amount_cents", 1);
        assertFalse(verifier.verify(transaction, hmac, SECRET));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsModifiedNestedFields() {
        String hmac = hmacOf(expectedConcatenated());

        Map<String, Object> changedOrder = transaction();
        ((Map<String, Object>) changedOrder.get("order")).put("id", 99999);
        assertFalse(verifier.verify(changedOrder, hmac, SECRET));

        Map<String, Object> changedSource = transaction();
        ((Map<String, Object>) changedSource.get("source_data")).put("pan", "9999");
        assertFalse(verifier.verify(changedSource, hmac, SECRET));
    }

    @Test
    void unsignedFieldsDoNotAffectSignature() {
        Map<String, Object> transaction = transaction();
        transaction.put("is_refund", true);
        transaction.put("profile_id", 12);
        assertTrue(verifier.verify(transaction, hmacOf(expectedConcatenated()), SECRET));
    }

    @Test
    void rejectsMissingHmacOrSecret() {
        String hmac = hmacOf(expectedConcatenated());
        assertFalse(verifier.verify(transaction(), null, SECRET));
        assertFalse(verifier.verify(transaction(), " ", SECRET));
        assertFalse(verifier.verify(transaction(), hmac, null));
        assertFalse(verifier.verify(transaction(), hmac, " "));
        assertFalse(verifier.verify(transaction(), hmac, "wrong-secret"));
        assertFalse(verifier.verify(null, hmac, SECRET));
    }

    @Test
    void rejectsTruncatedOrMalformedSignatures() {
        String hmac = hmacOf(expectedConcatenated());
        assertFalse(verifier.verify(transaction(), hmac.substring(0, 127), SECRET));
        assertFalse(verifier.verify(transaction(), hmac.substring(0, 64), SECRET));
        assertFalse(verifier.verify(transaction(), hmac + "0", SECRET));
        assertFalse(verifier.verify(transaction(), "z" + hmac.substring(1), SECRET));
    }

    @Test
    void matchesDocumentedConcatenationExample() {
        // Example from Paymob "HMAC Transaction Callback" documentation.
        Map<String, Object> sourceData = new HashMap<>();
        sourceData.put("pan", "2346");
        sourceData.put("sub_type", "MasterCard");
        sourceData.put("type", "card");
        Map<String, Object> order = new HashMap<>();
        order.put("id", 217503754);
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("amount_cents", 100000);
        transaction.put("created_at", "2024-06-13T11:33:44.592345");
        transaction.put("currency", "EGP");
        transaction.put("error_occured", false);
        transaction.put("has_parent_transaction", false);
        transaction.put("id", 192036465);
        transaction.put("integration_id", 4097558);
        transaction.put("is_3d_secure", true);
        transaction.put("is_auth", false);
        transaction.put("is_capture", false);
        transaction.put("is_refunded", false);
        transaction.put("is_standalone_payment", true);
        transaction.put("is_voided", false);
        transaction.put("order", order);
        transaction.put("owner", 302852);
        transaction.put("pending", false);
        transaction.put("source_data", sourceData);
        transaction.put("success", true);

        assertEquals(
                "1000002024-06-13T11:33:44.592345EGPfalsefalse1920364654097558truefalsefalsefalsetruefalse217503754302852false2346MasterCardcardtrue",
                verifier.concatenate(transaction));
    }

    @Test
    void integralNumbersAreRenderedWithoutFraction() {
        Map<String, Object> asLong = transaction();
        asLong.put("amount_cents", 50000L);
        Map<String, Object> asDouble = transaction();
        asDouble.put("amount_cents", 50000.0);
        assertEquals(verifier.concatenate(transaction()), verifier.concatenate(asLong));
        assertEquals(verifier.concatenate(transaction()), verifier.concatenate(asDouble));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingOrNullSignedFieldsContributeEmptyString() {
        Map<String, Object> transaction = transaction();
        ((Map<String, Object>) transaction.get("source_data")).remove("sub_type");
        transaction.put("created_at", null);
        String expected = "50000EGPfalsefalse9876543214321truefalsetruefalsefalsefalse1234542false2346cardtrue";
        assertEquals(expected, verifier.concatenate(transaction));
        assertTrue(verifier.verify(transaction, hmacOf(expected), SECRET));
    }

    @Test
    void missingNestedObjectIsTreatedAsMissingFields() {
        Map<String, Object> transaction = transaction();
        transaction.remove("source_data");
        assertFalse(verifier.verify(transaction, hmacOf(expectedConcatenated()), SECRET));
        assertTrue(verifier.verify(transaction, hmacOf(verifier.concatenate(transaction)), SECRET));
    }

    @Test
    void rejectsStructuredValueInSignedField() {
        Map<String, Object> transaction = transaction();
        transaction.put("currency", Map.of("code", "EGP"));
        assertFalse(verifier.verify(transaction, hmacOf(expectedConcatenated()), SECRET));
        transaction.put("currency", List.of("EGP"));
        assertFalse(verifier.verify(transaction, hmacOf(expectedConcatenated()), SECRET));
    }

    @Test
    void verifiesRedirectQueryParameters() {
        Map<String, String> params = redirectParams("order");
        String hmac = hmacOf(expectedConcatenated());
        assertTrue(verifier.verifyRedirect(params, hmac, SECRET));
        assertTrue(verifier.verifyRedirect(redirectParams("order_id"), hmac, SECRET));

        params.put("success", "false");
        assertFalse(verifier.verifyRedirect(params, hmac, SECRET));
        assertFalse(verifier.verifyRedirect(null, hmac, SECRET));
    }

    private Map<String, String> redirectParams(String orderKey) {
        Map<String, String> params = new HashMap<>();
        params.put("amount_cents", "50000");
        params.put("created_at", "2024-01-01T10:00:00Z");
        params.put("currency", "EGP");
        params.put("error_occured", "false");
        params.put("has_parent_transaction", "false");
        params.put("id", "987654321");
        params.put("integration_id", "4321");
        params.put("is_3d_secure", "true");
        params.put("is_auth", "false");
        params.put("is_capture", "true");
        params.put("is_refunded", "false");
        params.put("is_standalone_payment", "false");
        params.put("is_voided", "false");
        params.put(orderKey, "12345");
        params.put("owner", "42");
        params.put("pending", "false");
        params.put("source_data.pan", "2346");
        params.put("source_data.sub_type", "debit_card");
        params.put("source_data.type", "card");
        params.put("success", "true");
        params.put("data.message", "Approved");
        return params;
    }

    private String expectedConcatenated() {
        return "500002024-01-01T10:00:00ZEGPfalsefalse"
                + "9876543214321"
                + "truefalsetruefalsefalsefalse"
                + "12345"
                + "42false"
                + "2346debit_cardcardtrue";
    }

    private Map<String, Object> transaction() {
        Map<String, Object> sourceData = new HashMap<>();
        sourceData.put("pan", "2346");
        sourceData.put("sub_type", "debit_card");
        sourceData.put("type", "card");
        Map<String, Object> order = new HashMap<>();
        order.put("id", 12345);
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("amount_cents", 50000);
        transaction.put("created_at", "2024-01-01T10:00:00Z");
        transaction.put("currency", "EGP");
        transaction.put("error_occured", false);
        transaction.put("has_parent_transaction", false);
        transaction.put("id", 987654321);
        transaction.put("integration_id", 4321);
        transaction.put("is_3d_secure", true);
        transaction.put("is_auth", false);
        transaction.put("is_capture", true);
        transaction.put("is_refunded", false);
        transaction.put("is_standalone_payment", false);
        transaction.put("is_voided", false);
        transaction.put("order", order);
        transaction.put("owner", 42);
        transaction.put("pending", false);
        transaction.put("source_data", sourceData);
        transaction.put("success", true);
        return transaction;
    }
}
