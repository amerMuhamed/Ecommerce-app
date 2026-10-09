package com.spring.eCommerce.service.payment.paymob;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spring.eCommerce.config.PaymobProperties;
import com.spring.eCommerce.dto.payment.PaymentInitiateRequest;
import com.spring.eCommerce.entity.Order;
import com.spring.eCommerce.entity.Payment;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.service.payment.paymob.dto.PaymobIntentionRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for Paymob's Intention API (POST {base-url}/v1/intention/, "Authorization: Token {secret key}").
 */
@Log4j2
@Component
public class PaymobClient {

    private final RestClient restClient;
    private final PaymobProperties properties;
    private final PaymobBillingMapper billingMapper;
    private final ObjectMapper objectMapper;

    public PaymobClient(@Qualifier("paymobRestClient") RestClient restClient,
                        PaymobProperties properties,
                        PaymobBillingMapper billingMapper,
                        ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.billingMapper = billingMapper;
        this.objectMapper = objectMapper;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public PaymobIntention createIntention(Payment payment, Order order, PaymentInitiateRequest initiateRequest,
                                           String specialReference) {
        requireConfigured();
        long amountMinor = PaymobMoney.toMinorUnits(payment.getAmount(), payment.getCurrency());

        PaymobIntentionRequest request = new PaymobIntentionRequest(
                amountMinor,
                payment.getCurrency(),
                properties.getIntegrationIds(),
                billingMapper.items(order, amountMinor, payment.getCurrency()),
                billingMapper.billingData(payment.getPayer(), order, initiateRequest),
                billingMapper.customer(payment.getPayer(), order, initiateRequest),
                extras(payment, order),
                specialReference,
                properties.getExpirationSeconds() > 0 ? properties.getExpirationSeconds() : null,
                properties.getNotificationUrl().trim(),
                blankToNull(properties.getRedirectionUrl())
        );

        JsonNode body = post("/v1/intention/", request);
        return toIntention(body);
    }

    /**
     * Unified Checkout URL as documented: {checkout-url}?publicKey={public key}&clientSecret={client secret}.
     */
    public String buildCheckoutUrl(String clientSecret) {
        return UriComponentsBuilder.fromUriString(properties.getCheckoutUrl().trim())
                .queryParam("publicKey", properties.getPublicKey())
                .queryParam("clientSecret", clientSecret)
                .encode()
                .build()
                .toUriString();
    }

    public String storedEnvelope(PaymobIntention intention) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("intentionId", intention.intentionId());
            envelope.put("intentionOrderId", intention.intentionOrderId());
            envelope.put("clientSecret", intention.clientSecret());
            envelope.put("status", intention.status());
            envelope.set("paymobResponse", intention.raw());
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new BusinessException("Failed to process Paymob response.");
        }
    }

    private PaymobIntention toIntention(JsonNode body) {
        if (body == null || !body.isObject()) {
            log.warn("Paymob create intention response is not a JSON object");
            throw new BusinessException("Paymob returned an invalid response. Please try again.");
        }
        String intentionId = text(body, "id");
        String clientSecret = text(body, "client_secret");
        String status = text(body, "status");
        JsonNode orderIdNode = body.get("intention_order_id");
        long intentionOrderId = orderIdNode != null && orderIdNode.canConvertToLong() && orderIdNode.asLong() > 0
                ? orderIdNode.asLong()
                : -1L;
        if (intentionId == null || clientSecret == null || intentionOrderId < 0) {
            log.warn("Paymob create intention response is missing id/client_secret/intention_order_id");
            throw new BusinessException("Paymob returned an invalid response. Please try again.");
        }
        return new PaymobIntention(intentionId, intentionOrderId, clientSecret, status, body);
    }

    private JsonNode post(String path, Object request) {
        try {
            String raw = restClient.post()
                    .uri(trimTrailingSlash(properties.getBaseUrl().trim()) + path)
                    .header("Authorization", "Token " + properties.getSecretKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                throw new BusinessException("Paymob returned an empty response. Please try again.");
            }
            return objectMapper.readTree(raw);
        } catch (HttpStatusCodeException ex) {
            throw paymobError(ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            // Timeout or connection failure: Paymob may or may not have created the intention, but its
            // client_secret never reached us or the customer, so it cannot be paid. A retry creates a new one.
            log.warn("Paymob request did not complete: {}", ex.getClass().getSimpleName());
            throw new BusinessException("Paymob did not respond in time. The payment was not started; please try again.");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Paymob response could not be processed: {}", ex.getClass().getSimpleName());
            throw new BusinessException("Paymob returned an invalid response. Please try again.");
        }
    }

    private BusinessException paymobError(HttpStatusCode status, String responseBody) {
        log.warn("Paymob API error: status={}", status.value());
        if (status.value() == 401 || status.value() == 403) {
            return new BusinessException("Paymob rejected our credentials. Please contact support.");
        }
        if (status.is5xxServerError()) {
            return new BusinessException("Paymob is temporarily unavailable. Please try again.");
        }
        String detail = extractDetail(responseBody);
        if (detail != null) {
            return new BusinessException("Paymob rejected the payment: " + detail);
        }
        return new BusinessException("Paymob request failed with status " + status.value() + ". Please try again.");
    }

    private String extractDetail(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            JsonNode detail = node.get("detail");
            if (detail != null && detail.isTextual() && !detail.asText().isBlank()) {
                return truncate(detail.asText(), 300);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Map<String, Object> extras(Payment payment, Order order) {
        Map<String, Object> extras = new HashMap<>();
        extras.put("ecom_payment_id", payment.getId());
        extras.put("ecom_order_id", order.getId());
        return extras;
    }

    private void requireConfigured() {
        List<String> missing = properties.missingRequiredSettings();
        if (!missing.isEmpty()) {
            throw new BusinessException("Paymob is not configured. Missing or invalid: " + String.join(", ", missing));
        }
    }

    private String trimTrailingSlash(String value) {
        if (value != null && value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || !child.isTextual() || child.asText().isBlank()) {
            return null;
        }
        return child.asText();
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
