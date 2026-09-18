package com.spring.eCommerce.service.cart;

import com.spring.eCommerce.dto.cart.CartItemRequestDto;
import com.spring.eCommerce.dto.cart.CartItemResponseDto;
import com.spring.eCommerce.dto.cart.CartResponseDto;

public interface CartService {

    CartItemResponseDto addItemToCart(CartItemRequestDto cartItemRequestDto);

    CartResponseDto getCart();

    void clearCart();

}
