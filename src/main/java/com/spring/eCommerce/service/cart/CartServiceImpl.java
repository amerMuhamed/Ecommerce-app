package com.spring.eCommerce.service.cart;

import com.spring.eCommerce.dto.cart.CartItemRequestDto;
import com.spring.eCommerce.dto.cart.CartItemResponseDto;
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
    public CartItemResponseDto addItemToCart(CartItemRequestDto cartItemRequestDto) {

        Product product = productRepo.findByIdAndDeletedFalse(cartItemRequestDto.productId())
                .orElseThrow(() ->
                        new BusinessException("Product with id { " + cartItemRequestDto.productId() + " } not found"));

        AppUser user = userService.getCurrentUser();

        Cart userCart = user.getCart();

        if (userCart == null) {
            userCart = new Cart();
            user.setCart(userCart);
            userCart = cartRepo.save(userCart);
        }

        CartItem cartItem = userCart.getCartItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElse(null);

        int requestedQuantity = cartItemRequestDto.quantity();

        if (cartItem != null) {
            int newQuantity = cartItem.getQuantity() + requestedQuantity;

            if (product.getAvailableQuantity() < newQuantity) {
                throw new BusinessException("Not enough quantity available");
            }

            cartItem.setQuantity(newQuantity);

        } else {

            if (product.getAvailableQuantity() < requestedQuantity) {
                throw new BusinessException("Not enough quantity available");
            }

            cartItem = new CartItem();
            cartItem.setProduct(product);
            cartItem.setQuantity(requestedQuantity);

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

    @Override
    @Transactional
    public CartItemResponseDto updateItemQuantity(Long productId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("Quantity must be greater than zero");
        }
        Product product = productRepo.findByIdAndDeletedFalse(productId)
                .orElseThrow(() ->
                        new BusinessException("Product with id { " + productId + " } not found"));
        if (product.getAvailableQuantity() < quantity) {
            throw new BusinessException("Not enough quantity available");
        }
        AppUser user = userService.getCurrentUser();
        Cart userCart = user.getCart();
        if (userCart == null) {
            throw new BusinessException("Cart is empty");
        }
        CartItem cartItem = userCart.getCartItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Product is not in the cart"));
        cartItem.setQuantity(quantity);
        return new CartItemResponseDto(cartItem.getId(), productId, quantity);
    }

    @Override
    @Transactional
    public void removeItem(Long productId) {
        AppUser user = userService.getCurrentUser();
        Cart userCart = user.getCart();
        if (userCart == null) {
            throw new BusinessException("Cart is empty");
        }
        CartItem cartItem = userCart.getCartItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Product is not in the cart"));
        userCart.removeItem(cartItem);
    }


}
