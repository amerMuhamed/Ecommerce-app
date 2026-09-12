package com.spring.eCommerce.dto.cart;

public record CartRequestDto(
        Long productId,
        int quantity
) {
}
