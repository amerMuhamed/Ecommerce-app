package com.spring.eCommerce.service.payment;

import com.spring.eCommerce.dto.payment.PaymentInitiateRequest;
import com.spring.eCommerce.entity.Order;
import com.spring.eCommerce.entity.Payment;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.entity.enums.PaymentStatus;
import com.spring.eCommerce.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Development-only gateway: its webhooks are unsigned and always verify as successful, so it is only
 * registered when {@code payment.mock.enabled=true}. Never enable it in production.
 */
@Component
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "true")
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.MOCK;
    }

    @Override
    public InitiateResult initiate(Payment payment, Order order, PaymentInitiateRequest request) {
        String reference = "mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String checkoutUrl = "https://mock-pay.local/checkout/" + reference;
        return new InitiateResult(reference, checkoutUrl, "{\"mock\":true,\"reference\":\"" + reference + "\"}");
    }

    @Override
    public VerifyResult verify(String providerReference) {
        return new VerifyResult(PaymentStatus.SUCCEEDED, "{\"mock\":true,\"reference\":\"" + providerReference + "\"}");
    }

    @Override
    public WebhookEvent parseWebhook(Map<String, Object> rawBody) {
        Object eventId = rawBody.get("eventId");
        Object reference = rawBody.get("providerReference");
        if (eventId == null || reference == null) {
            throw new BusinessException("Invalid mock webhook payload: expected eventId and providerReference");
        }
        return new WebhookEvent(eventId.toString(), reference.toString());
    }

    @Override
    public boolean verifyWebhookSignature(Map<String, Object> rawBody, Map<String, String> headers,
                                          Map<String, String> queryParams) {
        return true;
    }
}
