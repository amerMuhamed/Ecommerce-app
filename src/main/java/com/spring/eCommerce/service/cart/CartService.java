package com.spring.eCommerce.service.cart;

import com.spring.eCommerce.dto.cart.CartRequestDto;
import com.spring.eCommerce.dto.cart.CartResponseDto;
import com.spring.eCommerce.service.comman.CommonService;

public interface CartService extends CommonService<CartRequestDto, CartResponseDto> {

    CartResponseDto addItemToCart(CartRequestDto cartRequestDto);

}
