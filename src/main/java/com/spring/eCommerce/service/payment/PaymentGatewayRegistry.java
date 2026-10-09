package com.spring.eCommerce.service.payment;

import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentGatewayRegistry {

    private final List<PaymentGateway> gateways;

    public PaymentGateway get(PaymentProvider provider) {
        return gateways.stream()
                .filter(gateway -> gateway.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new BusinessException("No payment gateway configured for provider: " + provider));
    }
}
