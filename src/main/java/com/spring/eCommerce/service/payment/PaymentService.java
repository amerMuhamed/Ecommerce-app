package com.spring.eCommerce.service.payment;

import com.spring.eCommerce.dto.payment.PaymentInitiateRequest;
import com.spring.eCommerce.dto.payment.PaymentResponseDto;

import java.util.List;

public interface PaymentService {
    PaymentResponseDto initiatePayment(Long orderId, PaymentInitiateRequest request);

    PaymentResponseDto getPaymentByOrderId(Long orderId);

    PaymentResponseDto getPaymentById(Long paymentId);

    List<PaymentResponseDto> getMyPayments();
}
