package com.spring.eCommerce.dto.cart;

public record CartItemRequestDto(
        Long productId,
        int quantity
) {
}
