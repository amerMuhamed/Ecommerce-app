package com.spring.eCommerce.web.controller;

import com.spring.eCommerce.dto.payment.PaymentReturnResponseDto;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.service.payment.PaymentGatewayRegistry;
import com.spring.eCommerce.service.payment.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * Browser landing page after the provider's hosted checkout, e.g. GET /payments/return/paymob.
 * Verifies the redirect signature, then sends the customer back to their order page.
 * Read-only: payment state is only changed by verified webhooks; the redirect's own success flag is ignored.
 */
@Controller
@RequiredArgsConstructor
public class PaymentReturnWebController {

    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentWebhookService webhookService;

    @GetMapping("/payments/return/{provider}")
    public String handleReturn(@PathVariable("provider") String providerSegment,
                               @RequestParam Map<String, String> queryParams,
                               RedirectAttributes redirectAttributes) {
        PaymentReturnResponseDto status;
        try {
            PaymentProvider provider = PaymentProvider.fromPathSegment(providerSegment)
                    .orElseThrow(() -> new BusinessException("Unknown payment provider."));
            String providerReference = gatewayRegistry.get(provider).verifyReturnRedirect(queryParams).orElse(null);
            if (providerReference == null) {
                redirectAttributes.addFlashAttribute("orderNotice",
                        "We could not verify the payment redirect. Check your order's payment status below.");
                return "redirect:/orders";
            }
            status = webhookService.getReturnStatus(provider, providerReference);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("orderNotice",
                    "We could not find the order for this payment. Check your orders below.");
            return "redirect:/orders";
        }
        redirectAttributes.addFlashAttribute("orderNotice", messageFor(status));
        return "redirect:/orders/" + status.orderId();
    }

    private String messageFor(PaymentReturnResponseDto status) {
        return switch (status.paymentStatus()) {
            case SUCCEEDED -> "Payment confirmed. Thank you!";
            case FAILED, EXPIRED, CANCELLED -> "The payment was not completed. You can pay on delivery instead.";
            case REFUNDED, PARTIALLY_REFUNDED -> "This payment has been refunded.";
            default -> "Your payment is being confirmed. Refresh this page in a moment to see the final status.";
        };
    }
}
