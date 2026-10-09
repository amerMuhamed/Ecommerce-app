package com.spring.eCommerce.service.payment;

import com.spring.eCommerce.Mapper.PaymentMapper;
import com.spring.eCommerce.config.PaymobProperties;
import com.spring.eCommerce.entity.Order;
import com.spring.eCommerce.entity.Payment;
import com.spring.eCommerce.entity.PaymentWebhookEvent;
import com.spring.eCommerce.entity.enums.OrderStatus;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.entity.enums.PaymentStatus;
import com.spring.eCommerce.entity.enums.WebhookEventStatus;
import com.spring.eCommerce.repository.OrderRepo;
import com.spring.eCommerce.repository.PaymentAttemptRepo;
import com.spring.eCommerce.repository.PaymentRepo;
import com.spring.eCommerce.repository.PaymentWebhookEventRepo;
import com.spring.eCommerce.service.payment.paymob.PaymobClient;
import com.spring.eCommerce.service.payment.paymob.PaymobHmacVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.spring.eCommerce.service.payment.PaymobPaymentGatewayTest.callback;
import static com.spring.eCommerce.service.payment.PaymobPaymentGatewayTest.transaction;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentWebhookServiceTest {

    private PaymentRepo paymentRepo;
    private PaymentAttemptRepo attemptRepo;
    private PaymentWebhookEventRepo eventRepo;
    private OrderRepo orderRepo;
    private PaymobPaymentGateway gateway;
    private PaymentWebhookService service;
    private Payment payment;
    private Order order;

    @BeforeEach
    void setUp() {
        paymentRepo = mock(PaymentRepo.class);
        attemptRepo = mock(PaymentAttemptRepo.class);
        eventRepo = mock(PaymentWebhookEventRepo.class);
        orderRepo = mock(OrderRepo.class);

        PaymobProperties properties = new PaymobProperties();
        properties.setHmacSecret(PaymobPaymentGatewayTest.SECRET);
        properties.setIntegrationIds(List.of(PaymobPaymentGatewayTest.INTEGRATION_ID));
        gateway = spy(new PaymobPaymentGateway(mock(PaymobClient.class), new PaymobHmacVerifier(), properties));
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(gateway));

        service = new PaymentWebhookService(paymentRepo, attemptRepo, eventRepo, orderRepo,
                Mappers.getMapper(PaymentMapper.class), registry);

        order = Order.builder().id(7L).orderStatus(OrderStatus.PENDING).build();
        payment = Payment.builder()
                .id(11L)
                .order(order)
                .provider(PaymentProvider.PAYMOB)
                .status(PaymentStatus.REQUIRES_ACTION)
                .amount(new BigDecimal("500.00"))
                .currency("EGP")
                .providerPaymentId("12345")
                .idempotencyKey("key")
                .build();

        when(paymentRepo.findByProviderAndProviderPaymentId(PaymentProvider.PAYMOB, "12345")).thenReturn(Optional.of(payment));
        when(paymentRepo.findByIdForUpdate(11L)).thenReturn(Optional.of(payment));
        when(eventRepo.findByProviderAndEventId(any(), any())).thenReturn(Optional.empty());
        when(attemptRepo.findPaymentIdsByProviderReference(any(), any())).thenReturn(List.of());
        when(paymentRepo.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void successfulCallbackMarksPaymentSucceededAndConfirmsOrder() {
        PaymentWebhookService.WebhookOutcome outcome = handle(transaction());

        assertEquals(WebhookEventStatus.PROCESSED, outcome.status());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(OrderStatus.CONFIRMED, order.getOrderStatus());
        assertEquals(PaymentStatus.SUCCEEDED, outcome.payment().status());
        PaymentWebhookEvent saved = savedEvent();
        assertEquals("987654321:success", saved.getEventId());
        assertSame(payment, saved.getPayment());
        assertEquals(WebhookEventStatus.PROCESSED, saved.getStatus());
    }

    @Test
    void providerReferenceMapsThroughPaymobOrderId() {
        handle(transaction());
        verify(paymentRepo).findByProviderAndProviderPaymentId(PaymentProvider.PAYMOB, "12345");
        verify(paymentRepo).findByIdForUpdate(11L);
    }

    @Test
    void callbackForEarlierAttemptReferenceFindsPayment() {
        payment.setProviderPaymentId("99999");
        Map<String, Object> transaction = transaction();
        when(paymentRepo.findByProviderAndProviderPaymentId(PaymentProvider.PAYMOB, "12345")).thenReturn(Optional.empty());
        when(attemptRepo.findPaymentIdsByProviderReference(PaymentProvider.PAYMOB, "12345")).thenReturn(List.of(11L));

        assertEquals(WebhookEventStatus.PROCESSED, handle(transaction).status());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
    }

    @Test
    void duplicateDeliveryIsNotReapplied() {
        PaymentWebhookEvent existing = PaymentWebhookEvent.builder()
                .provider(PaymentProvider.PAYMOB).eventId("987654321:success")
                .payment(payment).status(WebhookEventStatus.PROCESSED).build();
        when(eventRepo.findByProviderAndEventId(PaymentProvider.PAYMOB, "987654321:success")).thenReturn(Optional.of(existing));

        PaymentWebhookService.WebhookOutcome outcome = handle(transaction());

        assertEquals(WebhookEventStatus.PROCESSED, outcome.status());
        assertTrue(outcome.message().startsWith("Duplicate"));
        assertEquals(PaymentStatus.REQUIRES_ACTION, payment.getStatus());
        verify(eventRepo, never()).saveAndFlush(any());
        verify(paymentRepo, never()).save(any());
        verifyNoInteractions(orderRepo);
    }

    @Test
    void unknownPaymentReferenceIsQuarantined() {
        when(paymentRepo.findByProviderAndProviderPaymentId(any(), any())).thenReturn(Optional.empty());

        PaymentWebhookService.WebhookOutcome outcome = handle(transaction());

        assertEquals(WebhookEventStatus.FAILED, outcome.status());
        assertNull(savedEvent().getPayment());
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void ambiguousReferenceIsQuarantined() {
        when(paymentRepo.findByProviderAndProviderPaymentId(any(), any())).thenReturn(Optional.empty());
        when(attemptRepo.findPaymentIdsByProviderReference(PaymentProvider.PAYMOB, "12345")).thenReturn(List.of(11L, 12L));

        assertEquals(WebhookEventStatus.FAILED, handle(transaction()).status());
        assertEquals(PaymentStatus.REQUIRES_ACTION, payment.getStatus());
    }

    @Test
    void amountMismatchIsQuarantinedWithoutStateChange() {
        Map<String, Object> transaction = transaction();
        transaction.put("amount_cents", 100);

        PaymentWebhookService.WebhookOutcome outcome = handle(transaction);

        assertEquals(WebhookEventStatus.FAILED, outcome.status());
        assertEquals(PaymentStatus.REQUIRES_ACTION, payment.getStatus());
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());
        assertNotNull(savedEvent().getProcessingNote());
    }

    @Test
    void currencyMismatchIsQuarantinedWithoutStateChange() {
        Map<String, Object> transaction = transaction();
        transaction.put("currency", "USD");

        assertEquals(WebhookEventStatus.FAILED, handle(transaction).status());
        assertEquals(PaymentStatus.REQUIRES_ACTION, payment.getStatus());
    }

    @Test
    void failedCallbackMarksPaymentFailedAndKeepsOrderPending() {
        Map<String, Object> transaction = transaction();
        transaction.put("success", false);

        handle(transaction);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());
    }

    @Test
    void failureAfterSuccessCannotOverwrite() {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        Map<String, Object> transaction = transaction();
        transaction.put("id", 111);
        transaction.put("success", false);

        PaymentWebhookService.WebhookOutcome outcome = handle(transaction);

        assertEquals(WebhookEventStatus.IGNORED, outcome.status());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(OrderStatus.CONFIRMED, order.getOrderStatus());
    }

    @Test
    void latePendingAfterSuccessIsIgnored() {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        Map<String, Object> transaction = transaction();
        transaction.put("pending", true);
        transaction.put("success", false);

        assertEquals(WebhookEventStatus.IGNORED, handle(transaction).status());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
    }

    @Test
    void pendingThenSuccessForSameTransactionIsApplied() {
        Map<String, Object> pending = transaction();
        pending.put("pending", true);
        pending.put("success", false);
        handle(pending);
        assertEquals(PaymentStatus.PROCESSING, payment.getStatus());

        handle(transaction());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(OrderStatus.CONFIRMED, order.getOrderStatus());
    }

    @Test
    void successAfterFailedAttemptIsApplied() {
        payment.setStatus(PaymentStatus.FAILED);
        handle(transaction());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
    }

    @Test
    void secondSuccessfulTransactionIsFlaggedNotReapplied() {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        order.setOrderStatus(OrderStatus.SHIPPED);
        Map<String, Object> transaction = transaction();
        transaction.put("id", 222);

        PaymentWebhookService.WebhookOutcome outcome = handle(transaction);

        assertEquals(WebhookEventStatus.FAILED, outcome.status());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(OrderStatus.SHIPPED, order.getOrderStatus());
    }

    @Test
    void refundAfterSuccessIsRecordedAsRefundNotFailure() {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        Map<String, Object> refund = transaction();
        refund.put("id", 333);
        refund.put("has_parent_transaction", true);
        refund.put("is_refund", true);
        refund.put("amount_cents", 20000);

        handle(refund);
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, payment.getStatus());
        assertEquals(OrderStatus.CONFIRMED, order.getOrderStatus());

        refund.put("id", 334);
        refund.put("amount_cents", 50000);
        handle(refund);
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
    }

    @Test
    void callbackAfterRefundIsIgnored() {
        payment.setStatus(PaymentStatus.REFUNDED);
        assertEquals(WebhookEventStatus.IGNORED, handle(transaction()).status());
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
    }

    @Test
    void successForCancelledOrderDoesNotReopenOrder() {
        order.setOrderStatus(OrderStatus.CANCELLED);
        PaymentWebhookService.WebhookOutcome outcome = handle(transaction());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
        assertEquals(WebhookEventStatus.PROCESSED, outcome.status());
        assertNotNull(savedEvent().getProcessingNote());
    }

    @Test
    void processingErrorPropagatesSoTransactionRollsBack() {
        doThrow(new IllegalStateException("boom")).when(gateway).resolveWebhook(any(), any(), any());

        assertThrows(IllegalStateException.class, () -> handle(transaction()));
        verify(eventRepo, never()).saveAndFlush(any());
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void concurrentDuplicateInsertPropagatesForRetry() {
        when(eventRepo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertThrows(DataIntegrityViolationException.class, () -> handle(transaction()));
    }

    @Test
    void transitionTable() {
        assertTrue(PaymentWebhookService.isAllowedTransition(PaymentStatus.REQUIRES_ACTION, PaymentStatus.SUCCEEDED));
        assertTrue(PaymentWebhookService.isAllowedTransition(PaymentStatus.EXPIRED, PaymentStatus.SUCCEEDED));
        assertTrue(PaymentWebhookService.isAllowedTransition(PaymentStatus.SUCCEEDED, PaymentStatus.REFUNDED));
        assertTrue(PaymentWebhookService.isAllowedTransition(PaymentStatus.SUCCEEDED, PaymentStatus.CANCELLED));
        assertTrue(PaymentWebhookService.isAllowedTransition(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED));
        assertFalse(PaymentWebhookService.isAllowedTransition(PaymentStatus.SUCCEEDED, PaymentStatus.FAILED));
        assertFalse(PaymentWebhookService.isAllowedTransition(PaymentStatus.SUCCEEDED, PaymentStatus.EXPIRED));
        assertFalse(PaymentWebhookService.isAllowedTransition(PaymentStatus.REFUNDED, PaymentStatus.SUCCEEDED));
        assertFalse(PaymentWebhookService.isAllowedTransition(PaymentStatus.CANCELLED, PaymentStatus.SUCCEEDED));
        assertFalse(PaymentWebhookService.isAllowedTransition(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.FAILED));
    }

    @Test
    void returnStatusReadsServerSideState() {
        payment.setStatus(PaymentStatus.PROCESSING);
        when(paymentRepo.findById(11L)).thenReturn(Optional.of(payment));
        var status = service.getReturnStatus(PaymentProvider.PAYMOB, "12345");
        assertEquals(7L, status.orderId());
        assertEquals(PaymentStatus.PROCESSING, status.paymentStatus());
    }

    private PaymentWebhookService.WebhookOutcome handle(Map<String, Object> transaction) {
        Map<String, Object> body = callback(transaction);
        return service.handleWebhook(PaymentProvider.PAYMOB, gateway.parseWebhook(body), body, "{}");
    }

    private PaymentWebhookEvent savedEvent() {
        ArgumentCaptor<PaymentWebhookEvent> captor = ArgumentCaptor.forClass(PaymentWebhookEvent.class);
        verify(eventRepo, atLeastOnce()).saveAndFlush(captor.capture());
        return captor.getValue();
    }
}
