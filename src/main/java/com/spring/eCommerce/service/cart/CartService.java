package com.spring.eCommerce.service.cart;

import com.spring.eCommerce.dto.cart.CartItemResponseDto;
import com.spring.eCommerce.dto.cart.CartRequestDto;
import com.spring.eCommerce.dto.cart.CartResponseDto;

public interface CartService {

    CartItemResponseDto addItemToCart(CartRequestDto cartRequestDto);

    CartResponseDto getCart();

    void clearCart();

}
