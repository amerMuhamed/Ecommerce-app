package com.spring.eCommerce.service.payment;

import com.spring.eCommerce.config.PaymobProperties;
import com.spring.eCommerce.dto.payment.PaymentInitiateRequest;
import com.spring.eCommerce.entity.Order;
import com.spring.eCommerce.entity.Payment;
import com.spring.eCommerce.entity.PaymentAttempt;
import com.spring.eCommerce.entity.enums.PaymentAttemptStatus;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.entity.enums.PaymentStatus;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.service.payment.paymob.PaymobClient;
import com.spring.eCommerce.service.payment.paymob.PaymobHmacVerifier;
import com.spring.eCommerce.service.payment.paymob.PaymobIntention;
import com.spring.eCommerce.service.payment.paymob.PaymobMoney;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Paymob (Intention API + Unified Checkout).
 * <p>
 * Identifier mapping: {@code Payment.providerPaymentId} and {@code PaymentAttempt.providerReference} hold the
 * Paymob order id ({@code intention_order_id} of the Intention API), which transaction callbacks carry as the
 * HMAC-signed {@code obj.order.id}. The Paymob transaction id ({@code obj.id}) identifies the webhook event.
 * The {@code special_reference} sent on the intention is echoed (unsigned) in {@code obj.order.merchant_order_id}
 * and is only used as an additional consistency check.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class PaymobPaymentGateway implements PaymentGateway {

    static final String CALLBACK_TYPE_TRANSACTION = "TRANSACTION";
    private static final String SPECIAL_REFERENCE_PREFIX = "ecom-pay-";
    /**
     * Do not hand out a checkout whose intention is about to expire.
     */
    private static final long CHECKOUT_REUSE_MARGIN_SECONDS = 120;

    private final PaymobClient paymobClient;
    private final PaymobHmacVerifier hmacVerifier;
    private final PaymobProperties paymobProperties;

    /**
     * Unique per intention (Paymob requires a unique special_reference), so each attempt gets its own.
     */
    static String specialReference(Payment payment) {
        return SPECIAL_REFERENCE_PREFIX + payment.getId() + "-" + payment.getAttempts().size();
    }

    static boolean matchesSpecialReference(Payment payment, String merchantOrderId) {
        String legacy = SPECIAL_REFERENCE_PREFIX + payment.getId();
        return merchantOrderId.equals(legacy) || merchantOrderId.startsWith(legacy + "-");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map)) {
            throw new BusinessException("Invalid Paymob callback payload.");
        }
        return (Map<String, Object>) value;
    }

    private static long requiredLong(Map<String, Object> source, String field) {
        Long value = optionalLong(source, field);
        if (value == null) {
            throw new BusinessException("Invalid Paymob callback payload: missing " + field + ".");
        }
        return value;
    }

    private static Long optionalLong(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (value == null) {
            return null;
        }
        try {
            BigDecimal decimal = value instanceof Number ? new BigDecimal(value.toString()) : new BigDecimal(value.toString().trim());
            BigInteger integral = decimal.toBigIntegerExact();
            return integral.longValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new BusinessException("Invalid Paymob callback payload: " + field + " is not an integer.");
        }
    }

    private static String requiredString(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new BusinessException("Invalid Paymob callback payload: missing " + field + ".");
        }
        return text;
    }

    private static boolean requiredBoolean(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (!(value instanceof Boolean bool)) {
            throw new BusinessException("Invalid Paymob callback payload: missing " + field + ".");
        }
        return bool;
    }

    private static boolean optionalBoolean(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (value == null) {
            return false;
        }
        if (!(value instanceof Boolean bool)) {
            throw new BusinessException("Invalid Paymob callback payload: " + field + " is not a boolean.");
        }
        return bool;
    }

    @PostConstruct
    void reportConfiguration() {
        List<String> missing = paymobProperties.missingRequiredSettings();
        if (!missing.isEmpty()) {
            log.warn("Paymob payments are disabled until these settings are provided: {}", String.join(", ", missing));
        }
        String secretKey = paymobProperties.getSecretKey();
        if (secretKey != null && secretKey.contains("_live_")) {
            log.warn("Paymob is configured with LIVE credentials.");
        }
    }

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.PAYMOB;
    }

    @Override
    public InitiateResult initiate(Payment payment, Order order, PaymentInitiateRequest request) {
        PaymobIntention intention = paymobClient.createIntention(payment, order, request, specialReference(payment));
        return new InitiateResult(
                String.valueOf(intention.intentionOrderId()),
                paymobClient.buildCheckoutUrl(intention.clientSecret()),
                paymobClient.storedEnvelope(intention)
        );
    }

    /**
     * Paymob payment state is taken from HMAC-verified transaction callbacks (see {@link #resolveWebhook}),
     * which Paymob documents as the source of truth; there is no on-demand verification here.
     */
    @Override
    public VerifyResult verify(String providerReference) {
        throw new BusinessException("Paymob payments are confirmed only through HMAC-verified transaction callbacks.");
    }

    @Override
    public boolean isProcessableWebhook(Map<String, Object> rawBody) {
        return rawBody != null && CALLBACK_TYPE_TRANSACTION.equals(rawBody.get("type"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean verifyWebhookSignature(Map<String, Object> rawBody, Map<String, String> headers,
                                          Map<String, String> queryParams) {
        if (rawBody == null || queryParams == null) {
            return false;
        }
        Object obj = rawBody.get("obj");
        if (!(obj instanceof Map)) {
            return false;
        }
        return hmacVerifier.verify((Map<String, Object>) obj, queryParams.get("hmac"), paymobProperties.getHmacSecret());
    }

    @Override
    public WebhookEvent parseWebhook(Map<String, Object> rawBody) {
        if (!isProcessableWebhook(rawBody)) {
            throw new BusinessException("Unsupported Paymob callback type.");
        }
        Map<String, Object> transaction = map(rawBody.get("obj"));
        long transactionId = requiredLong(transaction, "id");
        long paymobOrderId = requiredLong(map(transaction.get("order")), "id");
        requiredLong(transaction, "amount_cents");
        requiredString(transaction, "currency");
        boolean success = requiredBoolean(transaction, "success");
        boolean pending = requiredBoolean(transaction, "pending");
        boolean voided = optionalBoolean(transaction, "is_voided");
        boolean refunded = optionalBoolean(transaction, "is_refunded");

        // Paymob re-sends the same transaction id when its state changes (e.g. pending -> paid, refunded),
        // so the event id includes the signed state flags. Exact redeliveries still share an id.
        String state = (pending ? "pending" : success ? "success" : "failure")
                + (voided ? ":voided" : "")
                + (refunded ? ":refunded" : "");
        return new WebhookEvent(transactionId + ":" + state, String.valueOf(paymobOrderId));
    }

    @Override
    public WebhookResolution resolveWebhook(Payment payment, WebhookEvent event, Map<String, Object> rawBody) {
        Map<String, Object> transaction = map(rawBody.get("obj"));
        Map<String, Object> order = map(transaction.get("order"));

        String currency = requiredString(transaction, "currency");
        if (!currency.equalsIgnoreCase(payment.getCurrency())) {
            return WebhookResolution.reject("Currency mismatch: callback " + currency + ", payment " + payment.getCurrency());
        }
        Long integrationId = optionalLong(transaction, "integration_id");
        List<Integer> integrationIds = paymobProperties.getIntegrationIds();
        if (integrationId == null || integrationIds == null
                || integrationIds.stream().noneMatch(id -> id != null && id.longValue() == integrationId)) {
            return WebhookResolution.reject("Callback integration id is not one of the configured integration ids.");
        }
        Object merchantOrderId = order.get("merchant_order_id");
        if (merchantOrderId != null && !matchesSpecialReference(payment, merchantOrderId.toString())) {
            return WebhookResolution.reject("Callback merchant reference does not belong to this payment.");
        }

        long expectedMinor = PaymobMoney.toMinorUnits(payment.getAmount(), payment.getCurrency());
        long amountMinor = requiredLong(transaction, "amount_cents");
        boolean success = requiredBoolean(transaction, "success");
        boolean pending = requiredBoolean(transaction, "pending");

        if (optionalBoolean(transaction, "has_parent_transaction")) {
            return resolveFollowUpTransaction(transaction, success, pending, amountMinor, expectedMinor);
        }

        if (amountMinor != expectedMinor) {
            return WebhookResolution.reject("Amount mismatch: callback " + amountMinor + ", expected " + expectedMinor);
        }
        if (optionalBoolean(transaction, "is_voided")) {
            return WebhookResolution.apply(PaymentStatus.CANCELLED, null);
        }
        if (optionalBoolean(transaction, "is_refunded")) {
            Long refundedMinor = optionalLong(transaction, "refunded_amount_cents");
            return WebhookResolution.apply(refundedMinor != null && refundedMinor >= expectedMinor
                    ? PaymentStatus.REFUNDED
                    : PaymentStatus.PARTIALLY_REFUNDED, null);
        }
        if (pending) {
            return WebhookResolution.apply(PaymentStatus.PROCESSING, null);
        }
        return WebhookResolution.apply(success ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED, null);
    }

    /**
     * Refund / void transactions are separate Paymob transactions linked to the original payment
     * ({@code has_parent_transaction=true}) on the same Paymob order. Their kind is given by {@code is_refund} /
     * {@code is_void}, which are not part of the HMAC; the signed fields bind the callback to a genuine
     * Paymob transaction on this order, and a follow-up can only reduce what was collected.
     */
    private WebhookResolution resolveFollowUpTransaction(Map<String, Object> transaction, boolean success,
                                                         boolean pending, long amountMinor, long expectedMinor) {
        if (!success || pending) {
            return WebhookResolution.ignore("Follow-up transaction was not completed successfully.");
        }
        if (amountMinor <= 0 || amountMinor > expectedMinor) {
            return WebhookResolution.reject("Follow-up transaction amount " + amountMinor + " is outside the payment amount.");
        }
        if (optionalBoolean(transaction, "is_void")) {
            return WebhookResolution.apply(PaymentStatus.CANCELLED, null);
        }
        if (optionalBoolean(transaction, "is_refund")) {
            return WebhookResolution.apply(amountMinor == expectedMinor
                    ? PaymentStatus.REFUNDED
                    : PaymentStatus.PARTIALLY_REFUNDED, null);
        }
        return WebhookResolution.ignore("Follow-up transaction type is not handled (not a refund or void).");
    }

    @Override
    public boolean isCheckoutReusable(Payment payment) {
        if (payment.getStatus() != PaymentStatus.REQUIRES_ACTION
                || payment.getProvider() != PaymentProvider.PAYMOB
                || payment.getCheckoutUrl() == null
                || paymobProperties.getExpirationSeconds() <= 0) {
            return false;
        }
        return payment.getAttempts().stream()
                .filter(attempt -> attempt.getStatus() == PaymentAttemptStatus.SUCCEEDED
                        && attempt.getProvider() == PaymentProvider.PAYMOB
                        && attempt.getCreatedDate() != null)
                .max(Comparator.comparing(PaymentAttempt::getAttemptNumber))
                .map(attempt -> attempt.getCreatedDate().toInstant()
                        .plusSeconds(paymobProperties.getExpirationSeconds() - CHECKOUT_REUSE_MARGIN_SECONDS)
                        .isAfter(Instant.now()))
                .orElse(false);
    }

    @Override
    public Optional<String> verifyReturnRedirect(Map<String, String> queryParams) {
        if (queryParams == null
                || !hmacVerifier.verifyRedirect(queryParams, queryParams.get("hmac"), paymobProperties.getHmacSecret())) {
            return Optional.empty();
        }
        String orderId = queryParams.get("order_id") != null ? queryParams.get("order_id") : queryParams.get("order");
        if (orderId == null || !orderId.matches("\\d{1,19}")) {
            return Optional.empty();
        }
        return Optional.of(orderId);
    }
}
