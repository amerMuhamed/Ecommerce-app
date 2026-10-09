package com.spring.eCommerce.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.eCommerce.dto.api.ApiResponse;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.entity.enums.WebhookEventStatus;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.service.payment.PaymentGateway;
import com.spring.eCommerce.service.payment.PaymentGatewayRegistry;
import com.spring.eCommerce.service.payment.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Server-to-server provider callbacks, e.g. POST /api/webhooks/payments/paymob.
 * Authenticated by the provider's signature (Paymob: HMAC in the {@code hmac} query parameter), not by JWT.
 */
@Log4j2
@RestController
@RequestMapping("/api/webhooks/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentWebhookService webhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/{provider}")
    public ResponseEntity<ApiResponse<?>> handleWebhook(
            @PathVariable("provider") String providerSegment,
            @RequestBody Map<String, Object> rawBody,
            @RequestHeader Map<String, String> headers,
            @RequestParam Map<String, String> queryParams) {
        PaymentProvider provider = PaymentProvider.fromPathSegment(providerSegment)
                .orElseThrow(() -> new BusinessException("Unknown payment provider."));
        PaymentGateway gateway = gatewayRegistry.get(provider);

        if (!gateway.isProcessableWebhook(rawBody)) {
            // Nothing from an unsupported callback type is trusted or applied; acknowledge so it is not retried.
            log.info("Ignored {} webhook of unsupported type", provider);
            return ResponseEntity.ok(new ApiResponse<>(true, "Webhook type ignored.", null));
        }

        if (!gateway.verifyWebhookSignature(rawBody, headers, queryParams)) {
            log.warn("Rejected {} webhook: invalid or missing signature", provider);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(false, "Invalid webhook signature.", null));
        }

        PaymentGateway.WebhookEvent event = gateway.parseWebhook(rawBody);
        PaymentWebhookService.WebhookOutcome outcome =
                webhookService.handleWebhook(provider, event, rawBody, writeRaw(rawBody));

        // The event is recorded in every case below, so acknowledge it to stop redelivery; quarantined events
        // (FAILED) are reported with success=false for visibility.
        return ResponseEntity.ok(new ApiResponse<>(
                outcome.status() != WebhookEventStatus.FAILED, outcome.message(), outcome.payment()));
    }

    private String writeRaw(Map<String, Object> rawBody) {
        try {
            return objectMapper.writeValueAsString(rawBody);
        } catch (Exception ex) {
            return null;
        }
    }
}
