package com.spring.eCommerce.service.cart;

import com.spring.eCommerce.dto.cart.CartItemResponseDto;
import com.spring.eCommerce.dto.cart.CartRequestDto;
import com.spring.eCommerce.dto.cart.CartResponseDto;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.entity.Cart;
import com.spring.eCommerce.entity.CartItem;
import com.spring.eCommerce.entity.Product;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.repository.CartRepo;
import com.spring.eCommerce.repository.ProductRepo;
import com.spring.eCommerce.service.user.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {
    private final UserService userService;
    private final ProductRepo productRepo;
    private final CartRepo cartRepo;

    @Override
    @Transactional
    public CartItemResponseDto addItemToCart(CartRequestDto cartRequestDto) {

        Product product = productRepo.findById(cartRequestDto.productId())
                .orElseThrow(() -> new BusinessException("Product with id { " + cartRequestDto.productId() + " } not found"));
        int productQuantity = product.getAvailableQuantity();
        if (productQuantity < cartRequestDto.quantity()) {
            throw new BusinessException("Not enough quantity available");
        }

        AppUser user = userService.getCurrentUser();
        Cart userCart = user.getCart();
        if (userCart == null) {
            userCart = new Cart();
            user.setCart(userCart);
            userCart = cartRepo.save(userCart);
        }

        productQuantity -= cartRequestDto.quantity();
        product.setAvailableQuantity(productQuantity);

        CartItem cartItem = userCart.getCartItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElse(null);

        if (cartItem != null) {
            cartItem.setQuantity(cartItem.getQuantity() + cartRequestDto.quantity());
        } else {
            cartItem = new CartItem();
            cartItem.setProduct(product);
            cartItem.setQuantity(cartRequestDto.quantity());
            userCart.addItem(cartItem);
        }

        return new CartItemResponseDto(cartItem.getId(), product.getId(), cartItem.getQuantity());
    }

    public CartResponseDto getCart() {
        AppUser user = userService.getCurrentUser();
        Cart userCart = user.getCart();
        if (userCart == null) {
            return null;
        }
        return new CartResponseDto(
                userCart.getCartItems().stream()
                        .map(cartItem -> new CartItemResponseDto(
                                cartItem.getId(),
                                cartItem.getProduct().getId(),
                                cartItem.getQuantity()
                        ))
                        .toList()
        );
    }

    @Transactional
    public void clearCart() {
        AppUser user = userService.getCurrentUser();
        Cart userCart = user.getCart();
        if (userCart != null) {
            userCart.clearItems();
        }
    }


}
