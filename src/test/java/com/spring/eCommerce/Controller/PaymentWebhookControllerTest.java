package com.spring.eCommerce.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.eCommerce.config.PaymobProperties;
import com.spring.eCommerce.dto.payment.PaymentReturnResponseDto;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.entity.enums.PaymentStatus;
import com.spring.eCommerce.entity.enums.WebhookEventStatus;
import com.spring.eCommerce.exception.GlobalHandling;
import com.spring.eCommerce.service.payment.PaymentGateway;
import com.spring.eCommerce.service.payment.PaymentGatewayRegistry;
import com.spring.eCommerce.service.payment.PaymentWebhookService;
import com.spring.eCommerce.service.payment.PaymobPaymentGateway;
import com.spring.eCommerce.service.payment.paymob.PaymobClient;
import com.spring.eCommerce.service.payment.paymob.PaymobHmacVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static com.spring.eCommerce.service.payment.PaymobPaymentGatewayTest.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentWebhookControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PaymentWebhookService webhookService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PaymobProperties properties = new PaymobProperties();
        properties.setHmacSecret(SECRET);
        properties.setIntegrationIds(List.of(INTEGRATION_ID));
        PaymobPaymentGateway gateway = new PaymobPaymentGateway(mock(PaymobClient.class), new PaymobHmacVerifier(), properties);
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(gateway));
        webhookService = mock(PaymentWebhookService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PaymentWebhookController(registry, webhookService, objectMapper),
                        new PaymentReturnController(registry, webhookService))
                .setControllerAdvice(new GlobalHandling())
                .build();
    }

    @Test
    void signedCallbackOnLowercasePathIsProcessed() throws Exception {
        Map<String, Object> body = callback(transaction());
        when(webhookService.handleWebhook(eq(PaymentProvider.PAYMOB), any(), any(), any()))
                .thenReturn(new PaymentWebhookService.WebhookOutcome(WebhookEventStatus.PROCESSED, "Webhook processed.", null));

        mockMvc.perform(post("/api/webhooks/payments/paymob").param("hmac", sign(body))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webhookService).handleWebhook(eq(PaymentProvider.PAYMOB),
                eq(new PaymentGateway.WebhookEvent("987654321:success", "12345")), any(), any());
    }

    @Test
    void invalidSignatureIsRejectedBeforeAnyProcessing() throws Exception {
        Map<String, Object> body = callback(transaction());
        String hmac = sign(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> obj = (Map<String, Object>) body.get("obj");
        obj.put("amount_cents", 1);

        mockMvc.perform(post("/api/webhooks/payments/paymob").param("hmac", hmac)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/webhooks/payments/paymob")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(webhookService);
    }

    @Test
    void unsupportedCallbackTypeIsAcknowledgedWithoutProcessing() throws Exception {
        mockMvc.perform(post("/api/webhooks/payments/paymob")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TOKEN\",\"obj\":{}}"))
                .andExpect(status().isOk());
        verifyNoInteractions(webhookService);
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/webhooks/payments/paymob")
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(webhookService);
    }

    @Test
    void signedButStructurallyInvalidCallbackIsBadRequest() throws Exception {
        Map<String, Object> transaction = transaction();
        transaction.put("success", "yes");
        Map<String, Object> body = callback(transaction);
        mockMvc.perform(post("/api/webhooks/payments/paymob").param("hmac", sign(body))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(webhookService);
    }

    @Test
    void unknownProviderIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/webhooks/payments/unknown")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quarantinedEventIsAcknowledgedWithSuccessFalse() throws Exception {
        Map<String, Object> body = callback(transaction());
        when(webhookService.handleWebhook(any(), any(), any(), any()))
                .thenReturn(new PaymentWebhookService.WebhookOutcome(WebhookEventStatus.FAILED, "Webhook rejected: Amount mismatch", null));
        mockMvc.perform(post("/api/webhooks/payments/paymob").param("hmac", sign(body))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void returnRedirectShowsServerSideStatusOnly() throws Exception {
        when(webhookService.getReturnStatus(PaymentProvider.PAYMOB, "12345"))
                .thenReturn(new PaymentReturnResponseDto(7L, PaymentStatus.PROCESSING));
        Map<String, Object> transaction = transaction();

        mockMvc.perform(get("/api/payments/return/paymob")
                        .param("amount_cents", "50000").param("created_at", "2024-01-01T10:00:00Z")
                        .param("currency", "EGP").param("error_occured", "false")
                        .param("has_parent_transaction", "false").param("id", "987654321")
                        .param("integration_id", String.valueOf(INTEGRATION_ID)).param("is_3d_secure", "true")
                        .param("is_auth", "false").param("is_capture", "false").param("is_refunded", "false")
                        .param("is_standalone_payment", "true").param("is_voided", "false").param("order", "12345")
                        .param("owner", "42").param("pending", "false").param("source_data.pan", "2346")
                        .param("source_data.sub_type", "MasterCard").param("source_data.type", "card")
                        .param("success", "true").param("hmac", sign(callback(transaction))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.data.orderId").value(7));
        verify(webhookService, never()).handleWebhook(any(), any(), any(), any());
    }

    @Test
    void unverifiedReturnRedirectIsRejected() throws Exception {
        mockMvc.perform(get("/api/payments/return/paymob").param("order", "12345").param("success", "true"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(webhookService);
    }
}
