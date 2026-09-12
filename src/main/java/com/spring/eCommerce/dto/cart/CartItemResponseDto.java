package com.spring.eCommerce.dto.cart;

public record CartItemResponseDto(
        Long cartItemId,
        Long productId,
        int quantity
) {
}
