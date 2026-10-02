package com.spring.eCommerce.dto.order;

import jakarta.validation.constraints.NotBlank;

public record OrderRequestDto(
        @NotBlank(message = "Shipping address is required")
        String shippingAddress
) {
}
