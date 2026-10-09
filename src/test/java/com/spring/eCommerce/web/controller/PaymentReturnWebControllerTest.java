package com.spring.eCommerce.web.controller;

import com.spring.eCommerce.config.PaymobProperties;
import com.spring.eCommerce.dto.payment.PaymentReturnResponseDto;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.entity.enums.PaymentStatus;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.service.payment.PaymentGatewayRegistry;
import com.spring.eCommerce.service.payment.PaymentWebhookService;
import com.spring.eCommerce.service.payment.PaymobPaymentGateway;
import com.spring.eCommerce.service.payment.paymob.PaymobClient;
import com.spring.eCommerce.service.payment.paymob.PaymobHmacVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static com.spring.eCommerce.service.payment.PaymobPaymentGatewayTest.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

class PaymentReturnWebControllerTest {

    private PaymentWebhookService webhookService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PaymobProperties properties = new PaymobProperties();
        properties.setHmacSecret(SECRET);
        properties.setIntegrationIds(List.of(INTEGRATION_ID));
        PaymobPaymentGateway gateway = new PaymobPaymentGateway(mock(PaymobClient.class), new PaymobHmacVerifier(), properties);
        webhookService = mock(PaymentWebhookService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PaymentReturnWebController(new PaymentGatewayRegistry(List.of(gateway)), webhookService)).build();
    }

    @Test
    void verifiedRedirectSendsCustomerToTheirOrderPage() throws Exception {
        when(webhookService.getReturnStatus(PaymentProvider.PAYMOB, "12345"))
                .thenReturn(new PaymentReturnResponseDto(7L, PaymentStatus.SUCCEEDED));

        mockMvc.perform(signedRedirect())
                .andExpect(redirectedUrl("/orders/7"))
                .andExpect(flash().attribute("orderNotice", "Payment confirmed. Thank you!"));
        verify(webhookService, never()).handleWebhook(any(), any(), any(), any());
    }

    @Test
    void pendingPaymentShowsBeingConfirmedNotice() throws Exception {
        when(webhookService.getReturnStatus(PaymentProvider.PAYMOB, "12345"))
                .thenReturn(new PaymentReturnResponseDto(7L, PaymentStatus.REQUIRES_ACTION));

        mockMvc.perform(signedRedirect())
                .andExpect(redirectedUrl("/orders/7"))
                .andExpect(flash().attribute("orderNotice",
                        "Your payment is being confirmed. Refresh this page in a moment to see the final status."));
    }

    @Test
    void unverifiedRedirectGoesToOrderListWithoutLookup() throws Exception {
        mockMvc.perform(get("/payments/return/paymob").param("order", "12345").param("success", "true"))
                .andExpect(redirectedUrl("/orders"));
        verifyNoInteractions(webhookService);
    }

    @Test
    void unknownProviderOrPaymentGoesToOrderList() throws Exception {
        mockMvc.perform(get("/payments/return/unknown")).andExpect(redirectedUrl("/orders"));
        when(webhookService.getReturnStatus(any(), any())).thenThrow(new BusinessException("Payment not found."));
        mockMvc.perform(signedRedirect()).andExpect(redirectedUrl("/orders"));
    }

    private MockHttpServletRequestBuilder signedRedirect() {
        return get("/payments/return/paymob")
                .param("amount_cents", "50000").param("created_at", "2024-01-01T10:00:00Z")
                .param("currency", "EGP").param("error_occured", "false")
                .param("has_parent_transaction", "false").param("id", "987654321")
                .param("integration_id", String.valueOf(INTEGRATION_ID)).param("is_3d_secure", "true")
                .param("is_auth", "false").param("is_capture", "false").param("is_refunded", "false")
                .param("is_standalone_payment", "true").param("is_voided", "false").param("order", "12345")
                .param("owner", "42").param("pending", "false").param("source_data.pan", "2346")
                .param("source_data.sub_type", "MasterCard").param("source_data.type", "card")
                .param("success", "true").param("hmac", sign(callback(transaction())));
    }
}
