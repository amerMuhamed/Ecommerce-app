package com.spring.eCommerce.Controller;

import com.spring.eCommerce.dto.api.ApiResponse;
import com.spring.eCommerce.dto.payment.PaymentReturnResponseDto;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.service.payment.PaymentGatewayRegistry;
import com.spring.eCommerce.service.payment.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Where the provider redirects the customer's browser after checkout, e.g. GET /api/payments/return/paymob.
 * Read-only: it never changes payment state. The status shown is the server-side status, which is only
 * updated by verified webhooks; the redirect's own success flag is ignored.
 */
@RestController
@RequestMapping("/api/payments/return")
@RequiredArgsConstructor
public class PaymentReturnController {

    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentWebhookService webhookService;

    @GetMapping("/{provider}")
    public ResponseEntity<ApiResponse<?>> handleReturn(
            @PathVariable("provider") String providerSegment,
            @RequestParam Map<String, String> queryParams) {
        PaymentProvider provider = PaymentProvider.fromPathSegment(providerSegment)
                .orElseThrow(() -> new BusinessException("Unknown payment provider."));

        String providerReference = gatewayRegistry.get(provider).verifyReturnRedirect(queryParams).orElse(null);
        if (providerReference == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(false,
                    "We could not verify this payment redirect. Check your order's payment status in your account.", null));
        }

        PaymentReturnResponseDto status = webhookService.getReturnStatus(provider, providerReference);
        return ResponseEntity.ok(new ApiResponse<>(true, messageFor(status), status));
    }

    private String messageFor(PaymentReturnResponseDto status) {
        return switch (status.paymentStatus()) {
            case SUCCEEDED -> "Payment confirmed.";
            case FAILED, EXPIRED, CANCELLED -> "The payment was not completed.";
            case REFUNDED, PARTIALLY_REFUNDED -> "This payment has been refunded.";
            default -> "Your payment is being confirmed. Check the order status again shortly.";
        };
    }
}
