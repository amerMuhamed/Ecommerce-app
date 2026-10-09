package com.spring.eCommerce.service.payment;

import com.spring.eCommerce.dto.payment.PaymentInitiateRequest;
import com.spring.eCommerce.entity.Order;
import com.spring.eCommerce.entity.Payment;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.entity.enums.PaymentStatus;
import com.spring.eCommerce.entity.enums.WebhookEventStatus;

import java.util.Map;
import java.util.Optional;

public interface PaymentGateway {

    PaymentProvider getProvider();

    /**
     * @param request the caller's initiation request; may be {@code null}. Carries checkout-only
     *                customer details (e.g. billing phone) that are forwarded to the provider and never persisted.
     */
    InitiateResult initiate(Payment payment, Order order, PaymentInitiateRequest request);

    VerifyResult verify(String providerReference);

    /**
     * Whether the webhook body is an event type this gateway processes. Unsupported types are acknowledged
     * without being trusted or applied.
     */
    default boolean isProcessableWebhook(Map<String, Object> rawBody) {
        return true;
    }

    /**
     * Must be called (and return true) before any field of the webhook body is trusted.
     */
    boolean verifyWebhookSignature(Map<String, Object> rawBody, Map<String, String> headers,
                                   Map<String, String> queryParams);

    WebhookEvent parseWebhook(Map<String, Object> rawBody);

    /**
     * Decides what a signature-verified webhook means for the given payment. The default asks the provider
     * for the payment state; gateways whose signed callbacks carry the state override this.
     */
    default WebhookResolution resolveWebhook(Payment payment, WebhookEvent event, Map<String, Object> rawBody) {
        VerifyResult verification = verify(event.providerReference());
        return WebhookResolution.apply(verification.status(), verification.rawResponse());
    }

    /**
     * Whether a previously created checkout for this payment can still be used, so initiating again
     * does not create a duplicate provider-side payment.
     */
    default boolean isCheckoutReusable(Payment payment) {
        return false;
    }

    /**
     * Verifies the customer-facing redirect and returns the provider reference it belongs to.
     * Empty when the redirect cannot be authenticated. Must never be used to change payment state.
     */
    default Optional<String> verifyReturnRedirect(Map<String, String> queryParams) {
        return Optional.empty();
    }

    record InitiateResult(
            String providerReference,
            String checkoutUrl,
            String rawResponse
    ) {
    }

    record VerifyResult(
            PaymentStatus status,
            String rawResponse
    ) {
    }

    record WebhookEvent(
            String eventId,
            String providerReference
    ) {
    }

    /**
     * @param outcome     PROCESSED (apply {@code status}), IGNORED (acknowledge, no state change) or
     *                    FAILED (quarantine, no state change)
     * @param rawResponse provider data to store on the payment, or {@code null} to keep the existing data
     */
    record WebhookResolution(
            WebhookEventStatus outcome,
            PaymentStatus status,
            String rawResponse,
            String reason
    ) {
        public static WebhookResolution apply(PaymentStatus status, String rawResponse) {
            return new WebhookResolution(WebhookEventStatus.PROCESSED, status, rawResponse, null);
        }

        public static WebhookResolution ignore(String reason) {
            return new WebhookResolution(WebhookEventStatus.IGNORED, null, null, reason);
        }

        public static WebhookResolution reject(String reason) {
            return new WebhookResolution(WebhookEventStatus.FAILED, null, null, reason);
        }
    }
}
