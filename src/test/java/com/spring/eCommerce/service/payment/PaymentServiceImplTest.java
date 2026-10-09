package com.spring.eCommerce.service.payment;

import com.spring.eCommerce.Mapper.PaymentMapper;
import com.spring.eCommerce.dto.payment.PaymentInitiateRequest;
import com.spring.eCommerce.dto.payment.PaymentResponseDto;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.entity.Order;
import com.spring.eCommerce.entity.Payment;
import com.spring.eCommerce.entity.enums.OrderStatus;
import com.spring.eCommerce.entity.enums.PaymentAttemptStatus;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.entity.enums.PaymentStatus;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.repository.OrderRepo;
import com.spring.eCommerce.repository.PaymentAttemptRepo;
import com.spring.eCommerce.repository.PaymentRepo;
import com.spring.eCommerce.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceImplTest {

    private final PaymentInitiateRequest request =
            new PaymentInitiateRequest(PaymentProvider.PAYMOB, null, "+201000000001", null);
    private PaymentRepo paymentRepo;
    private PaymentGateway gateway;
    private PaymentServiceImpl service;
    private AppUser user;
    private Order order;

    @BeforeEach
    void setUp() {
        OrderRepo orderRepo = mock(OrderRepo.class);
        paymentRepo = mock(PaymentRepo.class);
        PaymentAttemptRepo attemptRepo = mock(PaymentAttemptRepo.class);
        UserService userService = mock(UserService.class);
        gateway = mock(PaymentGateway.class);
        when(gateway.getProvider()).thenReturn(PaymentProvider.PAYMOB);

        service = new PaymentServiceImpl(orderRepo, paymentRepo, attemptRepo,
                Mappers.getMapper(PaymentMapper.class), new PaymentGatewayRegistry(List.of(gateway)), userService);

        user = new AppUser(3L, "Test Customer", "customer@example.test", null, Set.of());
        order = Order.builder().id(7L).appUser(user).orderStatus(OrderStatus.PENDING)
                .totalPrice(new BigDecimal("500.00")).build();
        when(userService.getCurrentUser()).thenReturn(user);
        when(orderRepo.findById(7L)).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrderId(7L)).thenReturn(Optional.empty());
        when(paymentRepo.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(11L);
            }
            return payment;
        });
    }

    @Test
    void storesPaymobOrderIdAndCheckoutUrl() {
        when(gateway.initiate(any(), any(), any())).thenReturn(new PaymentGateway.InitiateResult(
                "265715202", "https://eg.checkout.paymob.com/?publicKey=pk&clientSecret=cs", "{}"));

        PaymentResponseDto dto = service.initiatePayment(7L, request);

        assertEquals(PaymentStatus.REQUIRES_ACTION, dto.status());
        assertEquals("265715202", dto.providerPaymentId());
        assertEquals("https://eg.checkout.paymob.com/?publicKey=pk&clientSecret=cs", dto.checkoutUrl());
        assertEquals(new BigDecimal("500.00"), dto.amount());
        assertEquals("EGP", dto.currency());
        assertEquals("265715202", dto.attempts().get(0).providerReference());
    }

    @Test
    void providerFailureIsRecordedAsFailedAttempt() {
        when(gateway.initiate(any(), any(), any()))
                .thenThrow(new BusinessException("Paymob did not respond in time. The payment was not started; please try again."));

        assertThrows(BusinessException.class, () -> service.initiatePayment(7L, request));

        verify(paymentRepo, atLeastOnce()).save(argThat(payment -> payment.getStatus() == PaymentStatus.FAILED
                && payment.getAttempts().get(0).getStatus() == PaymentAttemptStatus.FAILED
                && payment.getProviderPaymentId() == null));
    }

    @Test
    void reusesValidCheckoutInsteadOfCreatingDuplicateIntention() {
        Payment existing = existingPayment(PaymentStatus.REQUIRES_ACTION);
        when(gateway.isCheckoutReusable(existing)).thenReturn(true);

        PaymentResponseDto dto = service.initiatePayment(7L, request);

        assertEquals("265715202", dto.providerPaymentId());
        verify(gateway, never()).initiate(any(), any(), any());
    }

    @Test
    void processingPaymentBlocksNewAttempt() {
        existingPayment(PaymentStatus.PROCESSING);
        assertThrows(BusinessException.class, () -> service.initiatePayment(7L, request));
        verify(gateway, never()).initiate(any(), any(), any());
    }

    @Test
    void paidOrderCannotBePaidAgain() {
        existingPayment(PaymentStatus.SUCCEEDED);
        assertThrows(BusinessException.class, () -> service.initiatePayment(7L, request));
        existingPayment(PaymentStatus.REFUNDED);
        assertThrows(BusinessException.class, () -> service.initiatePayment(7L, request));
    }

    @Test
    void idempotencyKeyOfAnotherUsersPaymentIsRejected() {
        Payment other = Payment.builder().id(99L)
                .order(Order.builder().id(8L).build())
                .payer(new AppUser(4L))
                .status(PaymentStatus.SUCCEEDED)
                .build();
        when(paymentRepo.findByIdempotencyKey("shared-key")).thenReturn(Optional.of(other));

        assertThrows(BusinessException.class, () -> service.initiatePayment(7L,
                new PaymentInitiateRequest(PaymentProvider.PAYMOB, "shared-key", "+201000000001", null)));
        verify(gateway, never()).initiate(any(), any(), any());
    }

    private Payment existingPayment(PaymentStatus status) {
        Payment payment = Payment.builder().id(11L).order(order).payer(user).provider(PaymentProvider.PAYMOB)
                .status(status).amount(new BigDecimal("500.00")).currency("EGP")
                .providerPaymentId("265715202").checkoutUrl("https://eg.checkout.paymob.com/?x").idempotencyKey("k")
                .build();
        when(paymentRepo.findByOrderId(7L)).thenReturn(Optional.of(payment));
        return payment;
    }
}
