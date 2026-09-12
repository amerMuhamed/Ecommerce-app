package com.spring.eCommerce.dto.cart;

import java.util.List;

public record CartResponseDto(
        List<CartItemResponseDto> cartItems
) {
}
