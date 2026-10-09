package com.spring.eCommerce.service.payment.paymob;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.eCommerce.config.PaymobProperties;
import com.spring.eCommerce.dto.payment.PaymentInitiateRequest;
import com.spring.eCommerce.entity.*;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.entity.enums.PaymentStatus;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.service.payment.PaymentGateway;
import com.spring.eCommerce.service.payment.PaymobPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Uses an in-memory HTTP stub (MockRestServiceServer); no request reaches Paymob.
 */
class PaymobClientTest {

    private static final String SECRET_KEY = "sk_test_unit";
    private static final String PUBLIC_KEY = "pk_test_unit";
    private static final String CREATED = """
            {"id":"pi_test_abc","client_secret":"cs_test_123","intention_order_id":265715202,
             "status":"intended","payment_keys":[],"special_reference":"ecom-pay-11-1"}
            """;
    private final PaymentInitiateRequest request =
            new PaymentInitiateRequest(PaymentProvider.PAYMOB, null, "+201000000001", null);
    private MockRestServiceServer server;
    private PaymobProperties properties;
    private PaymobClient client;
    private PaymobPaymentGateway gateway;
    private Payment payment;
    private Order order;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        properties = new PaymobProperties();
        properties.setSecretKey(SECRET_KEY);
        properties.setPublicKey(PUBLIC_KEY);
        properties.setHmacSecret("hmac");
        properties.setIntegrationIds(List.of(4321));
        properties.setNotificationUrl("https://example.test/api/webhooks/payments/paymob");
        properties.setRedirectionUrl("https://example.test/api/payments/return/paymob");

        client = new PaymobClient(builder.build(), properties, new PaymobBillingMapper(), new ObjectMapper());
        gateway = new PaymobPaymentGateway(client, new PaymobHmacVerifier(), properties);

        Product product = new Product();
        product.setName("Phone case");
        order = Order.builder().id(7L).totalPrice(new BigDecimal("500.00")).shippingAddress("1 Nile St").build();
        order.addOrderItem(OrderItem.builder().product(product).quantity(2).price(new BigDecimal("250.00")).build());

        payment = Payment.builder()
                .id(11L)
                .order(order)
                .payer(new AppUser(3L, "Test Customer", "customer@example.test", null, Set.of()))
                .provider(PaymentProvider.PAYMOB)
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("500.00"))
                .currency("EGP")
                .idempotencyKey("key")
                .build();
        payment.addAttempt(PaymentAttempt.builder().attemptNumber(1).build());
    }

    @Test
    void createsIntentionWithMinorUnitsAndBuildsCheckoutUrl() {
        server.expect(requestTo("https://accept.paymob.com/v1/intention/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Token " + SECRET_KEY))
                .andExpect(jsonPath("$.amount").value(50000))
                .andExpect(jsonPath("$.currency").value("EGP"))
                .andExpect(jsonPath("$.payment_methods[0]").value(4321))
                .andExpect(jsonPath("$.items[0].amount").value(25000))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.billing_data.phone_number").value("+201000000001"))
                .andExpect(jsonPath("$.billing_data.email").value("customer@example.test"))
                .andExpect(jsonPath("$.billing_data.first_name").value("Test"))
                .andExpect(jsonPath("$.billing_data.last_name").value("Customer"))
                .andExpect(jsonPath("$.special_reference").value("ecom-pay-11-1"))
                .andExpect(jsonPath("$.notification_url").value("https://example.test/api/webhooks/payments/paymob"))
                .andExpect(jsonPath("$.redirection_url").value("https://example.test/api/payments/return/paymob"))
                .andExpect(jsonPath("$.expiration").value(3600))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(CREATED));

        PaymentGateway.InitiateResult result = gateway.initiate(payment, order, request);

        assertEquals("265715202", result.providerReference());
        assertEquals("https://eg.checkout.paymob.com/?publicKey=pk_test_unit&clientSecret=cs_test_123", result.checkoutUrl());
        assertTrue(result.rawResponse().contains("\"intentionOrderId\":265715202"));
        assertFalse(result.rawResponse().contains(SECRET_KEY));
        server.verify();
    }

    @Test
    void omitsRedirectionUrlWhenNotConfigured() {
        properties.setRedirectionUrl(" ");
        server.expect(requestTo("https://accept.paymob.com/v1/intention/"))
                .andExpect(jsonPath("$.redirection_url").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(CREATED));
        client.createIntention(payment, order, request, "ecom-pay-11-1");
        server.verify();
    }

    @Test
    void sendsSingleSummaryItemWhenItemsDoNotAddUp() {
        payment.setAmount(new BigDecimal("450.00"));
        server.expect(requestTo("https://accept.paymob.com/v1/intention/"))
                .andExpect(jsonPath("$.amount").value(45000))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].amount").value(45000))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(CREATED));
        client.createIntention(payment, order, request, "ecom-pay-11-1");
        server.verify();
    }

    @Test
    void minorUnitConversion() {
        assertEquals(50000L, PaymobMoney.toMinorUnits(new BigDecimal("500"), "EGP"));
        assertEquals(50050L, PaymobMoney.toMinorUnits(new BigDecimal("500.5"), "EGP"));
        assertEquals(1L, PaymobMoney.toMinorUnits(new BigDecimal("0.01"), "egp"));
        assertEquals(50000L, PaymobMoney.toMinorUnits(new BigDecimal("500.0000"), "EGP"));
    }

    @Test
    void rejectsUnsupportedPrecisionAndInvalidAmounts() {
        assertThrows(BusinessException.class, () -> PaymobMoney.toMinorUnits(new BigDecimal("500.005"), "EGP"));
        assertThrows(BusinessException.class, () -> PaymobMoney.toMinorUnits(BigDecimal.ZERO, "EGP"));
        assertThrows(BusinessException.class, () -> PaymobMoney.toMinorUnits(new BigDecimal("-1"), "EGP"));
        assertThrows(BusinessException.class, () -> PaymobMoney.toMinorUnits(null, "EGP"));
        assertThrows(BusinessException.class, () -> PaymobMoney.toMinorUnits(BigDecimal.TEN, "USD"));
        assertThrows(BusinessException.class, () -> PaymobMoney.toMinorUnits(BigDecimal.TEN, null));

        payment.setAmount(new BigDecimal("500.001"));
        assertThrows(BusinessException.class, () -> client.createIntention(payment, order, request, "r"));
        server.verify();
    }

    @Test
    void missingConfigurationFailsBeforeCallingPaymobWithoutLeakingValues() {
        properties.setSecretKey(null);
        properties.setNotificationUrl("not-a-url");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.createIntention(payment, order, request, "r"));
        assertTrue(ex.getMessage().contains("payment.paymob.secret-key"));
        assertTrue(ex.getMessage().contains("payment.paymob.notification-url"));
        assertFalse(ex.getMessage().contains(PUBLIC_KEY));
        server.verify();
    }

    @Test
    void requiresBillingPhoneNumber() {
        PaymentInitiateRequest noPhone = new PaymentInitiateRequest(PaymentProvider.PAYMOB, null);
        assertThrows(BusinessException.class, () -> client.createIntention(payment, order, noPhone, "r"));
        PaymentInitiateRequest badPhone = new PaymentInitiateRequest(PaymentProvider.PAYMOB, null, "12ab", null);
        assertThrows(BusinessException.class, () -> client.createIntention(payment, order, badPhone, "r"));
        server.verify();
    }

    @Test
    void providerValidationErrorIsReported() {
        server.expect(requestTo("https://accept.paymob.com/v1/intention/"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Integration currency mismatch\"}"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.createIntention(payment, order, request, "r"));
        assertEquals("Paymob rejected the payment: Integration currency mismatch", ex.getMessage());
    }

    @Test
    void authenticationErrorIsReportedWithoutDetails() {
        server.expect(requestTo("https://accept.paymob.com/v1/intention/"))
                .andRespond(withUnauthorizedRequest().body("{\"detail\":\"Invalid token\"}"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.createIntention(payment, order, request, "r"));
        assertEquals("Paymob rejected our credentials. Please contact support.", ex.getMessage());
    }

    @Test
    void providerOutageIsReported() {
        server.expect(requestTo("https://accept.paymob.com/v1/intention/")).andRespond(withServiceUnavailable());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.createIntention(payment, order, request, "r"));
        assertEquals("Paymob is temporarily unavailable. Please try again.", ex.getMessage());
    }

    @Test
    void timeoutIsReportedAsNotStarted() {
        server.expect(requestTo("https://accept.paymob.com/v1/intention/"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.createIntention(payment, order, request, "r"));
        assertTrue(ex.getMessage().contains("did not respond in time"));
    }

    @Test
    void malformedResponseIsRejected() {
        server.expect(requestTo("https://accept.paymob.com/v1/intention/"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("<html>"));
        assertThrows(BusinessException.class, () -> client.createIntention(payment, order, request, "r"));
    }

    @Test
    void responseMissingClientSecretIsRejected() {
        server.expect(requestTo("https://accept.paymob.com/v1/intention/"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"pi_test\",\"intention_order_id\":1}"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.createIntention(payment, order, request, "r"));
        assertEquals("Paymob returned an invalid response. Please try again.", ex.getMessage());
    }

    @Test
    void emptyResponseIsRejected() {
        server.expect(requestTo("https://accept.paymob.com/v1/intention/"))
                .andRespond(withStatus(HttpStatus.CREATED));
        assertThrows(BusinessException.class, () -> client.createIntention(payment, order, request, "r"));
    }
}
