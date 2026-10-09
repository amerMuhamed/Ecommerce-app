package com.spring.eCommerce.service.payment;

import com.spring.eCommerce.Mapper.PaymentMapper;
import com.spring.eCommerce.dto.payment.PaymentInitiateRequest;
import com.spring.eCommerce.dto.payment.PaymentResponseDto;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.entity.Order;
import com.spring.eCommerce.entity.Payment;
import com.spring.eCommerce.entity.PaymentAttempt;
import com.spring.eCommerce.entity.enums.OrderStatus;
import com.spring.eCommerce.entity.enums.PaymentAttemptStatus;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.entity.enums.PaymentStatus;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.repository.OrderRepo;
import com.spring.eCommerce.repository.PaymentAttemptRepo;
import com.spring.eCommerce.repository.PaymentRepo;
import com.spring.eCommerce.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepo orderRepo;
    private final PaymentRepo paymentRepo;
    private final PaymentAttemptRepo paymentAttemptRepo;
    private final PaymentMapper paymentMapper;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final UserService userService;

    // A provider failure is persisted as a FAILED attempt instead of being rolled back with the request.
    @Transactional(noRollbackFor = BusinessException.class)
    @Override
    public PaymentResponseDto initiatePayment(Long orderId, PaymentInitiateRequest request) {
        AppUser appUser = userService.getCurrentUser();
        Order order = getOwnedOrder(orderId, appUser);

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("Cannot pay for a cancelled order.");
        }

        PaymentProvider provider = request != null && request.provider() != null
                ? request.provider()
                : PaymentProvider.MOCK;
        PaymentGateway gateway = gatewayRegistry.get(provider);

        if (request != null && request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Payment existing = paymentRepo.findByIdempotencyKey(request.idempotencyKey()).orElse(null);
            if (existing != null) {
                if (!existing.getOrder().getId().equals(orderId) || !existing.getPayer().getId().equals(appUser.getId())) {
                    throw new BusinessException("Idempotency key is already used for a different payment.");
                }
                return paymentMapper.toDto(existing);
            }
        }

        Payment payment = paymentRepo.findByOrderId(orderId).orElse(null);
        if (payment != null) {
            switch (payment.getStatus()) {
                case SUCCEEDED, PARTIALLY_REFUNDED, REFUNDED -> throw new BusinessException("Order is already paid.");
                case PROCESSING -> throw new BusinessException(
                        "A payment for this order is being processed. Please wait for its confirmation.");
                default -> {
                }
            }
            // Re-use a still-valid checkout instead of creating a second payable provider-side payment.
            if (payment.getProvider() == provider && gateway.isCheckoutReusable(payment)) {
                return paymentMapper.toDto(payment);
            }
            if (payment.getAmount().compareTo(order.getTotalPrice()) != 0) {
                throw new BusinessException("Order total no longer matches the payment amount.");
            }
        }

        if (payment == null) {
            payment = Payment.builder()
                    .order(order)
                    .payer(appUser)
                    .provider(provider)
                    .status(PaymentStatus.PENDING)
                    .amount(order.getTotalPrice())
                    .currency("EGP")
                    .idempotencyKey(request != null && request.idempotencyKey() != null && !request.idempotencyKey().isBlank()
                            ? request.idempotencyKey()
                            : UUID.randomUUID().toString())
                    .build();
            payment = paymentRepo.save(payment);
        } else {
            payment.setProvider(provider);
        }

        int attemptNumber = (int) (paymentAttemptRepo.countByPaymentId(payment.getId()) + 1);
        PaymentAttempt attempt = PaymentAttempt.builder()
                .payment(payment)
                .attemptNumber(attemptNumber)
                .provider(provider)
                .status(PaymentAttemptStatus.INITIATED)
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
        payment.addAttempt(attempt);

        try {
            PaymentGateway.InitiateResult result = gateway.initiate(payment, order, request);
            payment.setProviderPaymentId(result.providerReference());
            payment.setCheckoutUrl(result.checkoutUrl());
            payment.setProviderData(result.rawResponse());
            payment.setStatus(PaymentStatus.REQUIRES_ACTION);
            attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
            attempt.setProviderReference(result.providerReference());
            attempt.setResponsePayload(result.rawResponse());
        } catch (RuntimeException ex) {
            payment.setStatus(PaymentStatus.FAILED);
            attempt.setStatus(PaymentAttemptStatus.FAILED);
            attempt.setResponsePayload(ex instanceof BusinessException ? ex.getMessage() : ex.getClass().getSimpleName());
            paymentRepo.save(payment);
            throw ex;
        }

        return paymentMapper.toDto(paymentRepo.save(payment));
    }

    @Transactional(readOnly = true)
    @Override
    public PaymentResponseDto getPaymentByOrderId(Long orderId) {
        AppUser appUser = userService.getCurrentUser();
        getOwnedOrder(orderId, appUser);
        Payment payment = paymentRepo.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException("No payment found for order id { " + orderId + " }"));
        return paymentMapper.toDto(payment);
    }

    @Transactional(readOnly = true)
    @Override
    public PaymentResponseDto getPaymentById(Long paymentId) {
        AppUser appUser = userService.getCurrentUser();
        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new BusinessException("Payment with id { " + paymentId + " } not found"));
        if (!payment.getPayer().getId().equals(appUser.getId())) {
            throw new AccessDeniedException("You do not have permission to access this payment");
        }
        return paymentMapper.toDto(payment);
    }

    @Transactional(readOnly = true)
    @Override
    public List<PaymentResponseDto> getMyPayments() {
        AppUser appUser = userService.getCurrentUser();
        return paymentRepo.findByPayerId(appUser.getId())
                .stream()
                .map(paymentMapper::toDto)
                .toList();
    }

    private Order getOwnedOrder(Long orderId, AppUser appUser) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new BusinessException("Order with id { " + orderId + " } not found"));
        if (!order.getAppUser().getId().equals(appUser.getId())) {
            throw new AccessDeniedException("You do not have permission to access this order");
        }
        return order;
    }
}
