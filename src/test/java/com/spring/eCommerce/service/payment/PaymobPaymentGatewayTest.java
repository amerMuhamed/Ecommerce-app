package com.spring.eCommerce.service.payment;

import com.spring.eCommerce.config.PaymobProperties;
import com.spring.eCommerce.entity.Order;
import com.spring.eCommerce.entity.Payment;
import com.spring.eCommerce.entity.PaymentAttempt;
import com.spring.eCommerce.entity.enums.PaymentAttemptStatus;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.entity.enums.PaymentStatus;
import com.spring.eCommerce.entity.enums.WebhookEventStatus;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.service.payment.paymob.PaymobClient;
import com.spring.eCommerce.service.payment.paymob.PaymobHmacVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class PaymobPaymentGatewayTest {

    public static final String SECRET = "unit-test-hmac-secret";
    public static final int INTEGRATION_ID = 4321;

    private PaymobProperties properties;
    private PaymobPaymentGateway gateway;
    private Payment payment;

    private static void assertApplied(PaymentStatus expected, PaymentGateway.WebhookResolution resolution) {
        assertEquals(WebhookEventStatus.PROCESSED, resolution.outcome(), resolution.reason());
        assertEquals(expected, resolution.status());
    }

    // ---------- signature ----------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> order(Map<String, Object> transaction) {
        return (Map<String, Object>) transaction.get("order");
    }

    public static Map<String, Object> callback(Map<String, Object> transaction) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "TRANSACTION");
        body.put("obj", transaction);
        return body;
    }

    @SuppressWarnings("unchecked")
    public static String sign(Map<String, Object> body) {
        Map<String, Object> transaction = (Map<String, Object>) body.get("obj");
        Map<String, Object> source = (Map<String, Object>) transaction.get("source_data");
        String concatenated = "" + transaction.get("amount_cents") + transaction.get("created_at")
                + transaction.get("currency") + transaction.get("error_occured")
                + transaction.get("has_parent_transaction") + transaction.get("id")
                + transaction.get("integration_id") + transaction.get("is_3d_secure")
                + transaction.get("is_auth") + transaction.get("is_capture") + transaction.get("is_refunded")
                + transaction.get("is_standalone_payment") + transaction.get("is_voided")
                + order(transaction).get("id") + transaction.get("owner") + transaction.get("pending")
                + source.get("pan") + source.get("sub_type") + source.get("type") + transaction.get("success");
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
            mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(concatenated.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ---------- parsing ----------

    /**
     * Shape of the documented transaction processed callback {@code obj} (subset), for EGP 500.00.
     */
    public static Map<String, Object> transaction() {
        Map<String, Object> sourceData = new HashMap<>();
        sourceData.put("pan", "2346");
        sourceData.put("sub_type", "MasterCard");
        sourceData.put("type", "card");
        Map<String, Object> order = new HashMap<>();
        order.put("id", 12345);
        order.put("merchant_order_id", null);
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("id", 987654321);
        transaction.put("pending", false);
        transaction.put("amount_cents", 50000);
        transaction.put("success", true);
        transaction.put("is_auth", false);
        transaction.put("is_capture", false);
        transaction.put("is_standalone_payment", true);
        transaction.put("is_voided", false);
        transaction.put("is_refunded", false);
        transaction.put("is_3d_secure", true);
        transaction.put("integration_id", INTEGRATION_ID);
        transaction.put("has_parent_transaction", false);
        transaction.put("order", order);
        transaction.put("created_at", "2024-01-01T10:00:00Z");
        transaction.put("currency", "EGP");
        transaction.put("source_data", sourceData);
        transaction.put("error_occured", false);
        transaction.put("owner", 42);
        transaction.put("is_void", false);
        transaction.put("is_refund", false);
        transaction.put("refunded_amount_cents", 0);
        return transaction;
    }

    @BeforeEach
    void setUp() {
        properties = new PaymobProperties();
        properties.setHmacSecret(SECRET);
        properties.setIntegrationIds(List.of(INTEGRATION_ID));
        gateway = new PaymobPaymentGateway(mock(PaymobClient.class), new PaymobHmacVerifier(), properties);

        Order order = Order.builder().id(7L).build();
        payment = Payment.builder()
                .id(11L)
                .order(order)
                .provider(PaymentProvider.PAYMOB)
                .status(PaymentStatus.REQUIRES_ACTION)
                .amount(new BigDecimal("500.00"))
                .currency("EGP")
                .providerPaymentId("12345")
                .idempotencyKey("key")
                .build();
    }

    @Test
    void verifiesSignedCallback() {
        Map<String, Object> body = callback(transaction());
        assertTrue(gateway.verifyWebhookSignature(body, Map.of(), Map.of("hmac", sign(body))));
    }

    @Test
    void rejectsInvalidOrMissingSignature() {
        Map<String, Object> body = callback(transaction());
        String hmac = sign(body);
        assertFalse(gateway.verifyWebhookSignature(body, Map.of(), Map.of()));
        assertFalse(gateway.verifyWebhookSignature(body, Map.of(), Map.of("hmac", "abc")));
        assertFalse(gateway.verifyWebhookSignature(body, Map.of(), null));
        assertFalse(gateway.verifyWebhookSignature(Map.of("type", "TRANSACTION"), Map.of(), Map.of("hmac", hmac)));

        // HMAC sent in a header instead of the query string is not accepted.
        assertFalse(gateway.verifyWebhookSignature(body, Map.of("hmac", hmac), Map.of()));
    }

    @Test
    void rejectsSignatureWhenSecretMissing() {
        Map<String, Object> body = callback(transaction());
        String hmac = sign(body);
        properties.setHmacSecret(null);
        assertFalse(gateway.verifyWebhookSignature(body, Map.of(), Map.of("hmac", hmac)));
    }

    // ---------- resolution ----------

    @Test
    void parsesTransactionCallbackIdentifiers() {
        PaymentGateway.WebhookEvent event = gateway.parseWebhook(callback(transaction()));
        assertEquals("987654321:success", event.eventId());
        assertEquals("12345", event.providerReference());
    }

    @Test
    void acceptsCallbackWithoutMerchantOrderId() {
        Map<String, Object> transaction = transaction();
        order(transaction).remove("merchant_order_id");
        assertEquals("12345", gateway.parseWebhook(callback(transaction)).providerReference());
    }

    @Test
    void eventIdChangesWithTransactionState() {
        Map<String, Object> pending = transaction();
        pending.put("pending", true);
        pending.put("success", false);
        Map<String, Object> refunded = transaction();
        refunded.put("is_refunded", true);
        assertEquals("987654321:pending", gateway.parseWebhook(callback(pending)).eventId());
        assertEquals("987654321:success:refunded", gateway.parseWebhook(callback(refunded)).eventId());
    }

    @Test
    void rejectsMalformedCallbacks() {
        assertThrows(BusinessException.class, () -> gateway.parseWebhook(Map.of("type", "TOKEN", "obj", transaction())));
        assertThrows(BusinessException.class, () -> gateway.parseWebhook(Map.of("type", "TRANSACTION", "obj", "x")));

        Map<String, Object> noOrder = transaction();
        noOrder.remove("order");
        assertThrows(BusinessException.class, () -> gateway.parseWebhook(callback(noOrder)));

        Map<String, Object> noSuccess = transaction();
        noSuccess.remove("success");
        assertThrows(BusinessException.class, () -> gateway.parseWebhook(callback(noSuccess)));

        Map<String, Object> textId = transaction();
        textId.put("id", "abc");
        assertThrows(BusinessException.class, () -> gateway.parseWebhook(callback(textId)));
    }

    @Test
    void onlyTransactionCallbacksAreProcessable() {
        assertTrue(gateway.isProcessableWebhook(callback(transaction())));
        assertFalse(gateway.isProcessableWebhook(Map.of("type", "TOKEN")));
        assertFalse(gateway.isProcessableWebhook(Map.of()));
    }

    @Test
    void successfulTransactionResolvesToSucceeded() {
        assertApplied(PaymentStatus.SUCCEEDED, resolve(transaction()));
    }

    @Test
    void failedTransactionResolvesToFailed() {
        Map<String, Object> transaction = transaction();
        transaction.put("success", false);
        assertApplied(PaymentStatus.FAILED, resolve(transaction));
    }

    @Test
    void pendingTransactionResolvesToProcessing() {
        Map<String, Object> transaction = transaction();
        transaction.put("pending", true);
        transaction.put("success", false);
        assertApplied(PaymentStatus.PROCESSING, resolve(transaction));
    }

    @Test
    void voidedTransactionResolvesToCancelled() {
        Map<String, Object> transaction = transaction();
        transaction.put("is_voided", true);
        assertApplied(PaymentStatus.CANCELLED, resolve(transaction));
    }

    @Test
    void refundedTransactionDistinguishesFullAndPartialRefund() {
        Map<String, Object> full = transaction();
        full.put("is_refunded", true);
        full.put("refunded_amount_cents", 50000);
        assertApplied(PaymentStatus.REFUNDED, resolve(full));

        Map<String, Object> partial = transaction();
        partial.put("is_refunded", true);
        partial.put("refunded_amount_cents", 10000);
        assertApplied(PaymentStatus.PARTIALLY_REFUNDED, resolve(partial));
    }

    @Test
    void refundFollowUpTransactionResolvesToRefund() {
        Map<String, Object> refund = transaction();
        refund.put("has_parent_transaction", true);
        refund.put("is_refund", true);
        assertApplied(PaymentStatus.REFUNDED, resolve(refund));

        refund.put("amount_cents", 20000);
        assertApplied(PaymentStatus.PARTIALLY_REFUNDED, resolve(refund));

        refund.put("amount_cents", 60000);
        assertEquals(WebhookEventStatus.FAILED, resolve(refund).outcome());
    }

    @Test
    void unsuccessfulOrUnknownFollowUpTransactionIsIgnored() {
        Map<String, Object> failedRefund = transaction();
        failedRefund.put("has_parent_transaction", true);
        failedRefund.put("is_refund", true);
        failedRefund.put("success", false);
        assertEquals(WebhookEventStatus.IGNORED, resolve(failedRefund).outcome());

        Map<String, Object> capture = transaction();
        capture.put("has_parent_transaction", true);
        assertEquals(WebhookEventStatus.IGNORED, resolve(capture).outcome());
    }

    // ---------- checkout reuse / redirect ----------

    @Test
    void amountMismatchIsRejected() {
        Map<String, Object> transaction = transaction();
        transaction.put("amount_cents", 49999);
        assertEquals(WebhookEventStatus.FAILED, resolve(transaction).outcome());
    }

    @Test
    void currencyMismatchIsRejected() {
        Map<String, Object> transaction = transaction();
        transaction.put("currency", "USD");
        assertEquals(WebhookEventStatus.FAILED, resolve(transaction).outcome());
    }

    @Test
    void unknownIntegrationIsRejected() {
        Map<String, Object> transaction = transaction();
        transaction.put("integration_id", 999);
        assertEquals(WebhookEventStatus.FAILED, resolve(transaction).outcome());
    }

    // ---------- helpers ----------

    @Test
    void foreignMerchantReferenceIsRejected() {
        Map<String, Object> transaction = transaction();
        order(transaction).put("merchant_order_id", "ecom-pay-12-1");
        assertEquals(WebhookEventStatus.FAILED, resolve(transaction).outcome());

        order(transaction).put("merchant_order_id", "ecom-pay-11-3");
        assertApplied(PaymentStatus.SUCCEEDED, resolve(transaction));
    }

    @Test
    void paymobDoesNotOfferUnverifiedOnDemandVerification() {
        assertThrows(BusinessException.class, () -> gateway.verify("12345"));
    }

    @Test
    void checkoutIsReusableOnlyWhileIntentionIsValid() {
        properties.setExpirationSeconds(3600);
        payment.setCheckoutUrl("https://eg.checkout.paymob.com/?publicKey=pk&clientSecret=cs");
        PaymentAttempt attempt = PaymentAttempt.builder()
                .attemptNumber(1).provider(PaymentProvider.PAYMOB).status(PaymentAttemptStatus.SUCCEEDED).build();
        attempt.setCreatedDate(Date.from(Instant.now().minusSeconds(60)));
        payment.addAttempt(attempt);
        assertTrue(gateway.isCheckoutReusable(payment));

        attempt.setCreatedDate(Date.from(Instant.now().minusSeconds(3590)));
        assertFalse(gateway.isCheckoutReusable(payment));

        attempt.setCreatedDate(Date.from(Instant.now().minusSeconds(60)));
        payment.setStatus(PaymentStatus.FAILED);
        assertFalse(gateway.isCheckoutReusable(payment));
    }

    @Test
    void specialReferenceIsUniquePerAttempt() {
        payment.addAttempt(PaymentAttempt.builder().attemptNumber(1).build());
        assertEquals("ecom-pay-11-1", PaymobPaymentGateway.specialReference(payment));
        payment.addAttempt(PaymentAttempt.builder().attemptNumber(2).build());
        assertEquals("ecom-pay-11-2", PaymobPaymentGateway.specialReference(payment));
    }

    @Test
    void returnRedirectRequiresValidHmac() {
        Map<String, Object> transaction = transaction();
        Map<String, String> params = new HashMap<>();
        params.put("amount_cents", "50000");
        params.put("created_at", "2024-01-01T10:00:00Z");
        params.put("currency", "EGP");
        params.put("error_occured", "false");
        params.put("has_parent_transaction", "false");
        params.put("id", "987654321");
        params.put("integration_id", String.valueOf(INTEGRATION_ID));
        params.put("is_3d_secure", "true");
        params.put("is_auth", "false");
        params.put("is_capture", "false");
        params.put("is_refunded", "false");
        params.put("is_standalone_payment", "true");
        params.put("is_voided", "false");
        params.put("order", "12345");
        params.put("owner", "42");
        params.put("pending", "false");
        params.put("source_data.pan", "2346");
        params.put("source_data.sub_type", "MasterCard");
        params.put("source_data.type", "card");
        params.put("success", "true");
        params.put("hmac", sign(callback(transaction)));

        assertEquals("12345", gateway.verifyReturnRedirect(params).orElseThrow());

        params.put("hmac", "0".repeat(128));
        assertTrue(gateway.verifyReturnRedirect(params).isEmpty());
    }

    private PaymentGateway.WebhookResolution resolve(Map<String, Object> transaction) {
        Map<String, Object> body = callback(transaction);
        return gateway.resolveWebhook(payment, gateway.parseWebhook(body), body);
    }
}
