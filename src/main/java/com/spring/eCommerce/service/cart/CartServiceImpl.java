package com.spring.eCommerce.service.cart;

import com.spring.eCommerce.dto.cart.CartRequestDto;
import com.spring.eCommerce.dto.cart.CartResponseDto;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.entity.Cart;
import com.spring.eCommerce.entity.CartItem;
import com.spring.eCommerce.entity.Product;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.repository.CartItemRepo;
import com.spring.eCommerce.repository.CartRepo;
import com.spring.eCommerce.repository.ProductRepo;
import com.spring.eCommerce.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final ProductRepo productRepo;
    private final CartRepo cartRepo;
    private final CartItemRepo cartItemRepo;

    @Override
    public CartResponseDto addItemToCart(CartRequestDto cartRequestDto) {

        Product product = productRepo.findById(cartRequestDto.productId())
                .orElseThrow(() -> new BusinessException("Product not found"));

        if (product.getAvailableQuantity() < cartRequestDto.quantity()) {
            throw new BusinessException("Not enough quantity available");
        }

        AppUser user = UserService.getCurrentUser();
        Cart userCart = user.getCart();
        if (userCart == null) {
            userCart = new Cart();
            user.setCart(userCart);
            userCart = cartRepo.save(userCart);
        }

        CartItem cartItem = new CartItem();
        cartItem.setProduct(product);
        cartItem.setCart(userCart);
        cartItem.setQuantity(cartRequestDto.quantity());
        cartItem = cartItemRepo.save(cartItem);

        return new CartResponseDto(cartItem.getId(), product.getId(), cartItem.getQuantity());
    }

    @Override
    public List<CartResponseDto> getAll() {
        return List.of();
    }

    @Override
    public CartResponseDto getById(Long id) {
        return null;
    }

    @Override
    public CartResponseDto save(CartRequestDto obj) {

        return null;
    }

    @Override
    public void deleteById(Long id) {

    }

    @Override
    public CartResponseDto update(Long id, CartRequestDto obj) {
        return null;
    }


}
