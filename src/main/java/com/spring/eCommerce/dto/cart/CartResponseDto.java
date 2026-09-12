package com.spring.eCommerce.dto.cart;

public record CartResponseDto(
        Long id,
        Long productId,
        int quantity
) {
}
