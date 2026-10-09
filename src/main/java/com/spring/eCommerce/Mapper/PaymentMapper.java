package com.spring.eCommerce.Mapper;

import com.spring.eCommerce.dto.payment.PaymentAttemptResponseDto;
import com.spring.eCommerce.dto.payment.PaymentResponseDto;
import com.spring.eCommerce.entity.Payment;
import com.spring.eCommerce.entity.PaymentAttempt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "orderId", source = "order.id")
    PaymentResponseDto toDto(Payment payment);

    PaymentAttemptResponseDto toDto(PaymentAttempt attempt);
}
