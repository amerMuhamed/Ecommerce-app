package com.spring.eCommerce.Controller;

import com.spring.eCommerce.dto.api.ApiResponse;
import com.spring.eCommerce.dto.cart.CartItemRequestDto;
import com.spring.eCommerce.service.cart.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController()
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    @PostMapping("/item")
    public ResponseEntity<ApiResponse<?>> addProductToCart(@RequestBody CartItemRequestDto cartItemRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new ApiResponse<>(true, "Product added to cart successfully", cartService.addItemToCart(cartItemRequestDto))
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getUserCart() {
        return ResponseEntity.status(HttpStatus.OK).body(
                new ApiResponse<>(true, "User cart retrieved successfully", cartService.getCart())
        );
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<?>> deleteUserCart() {
        cartService.clearCart();
        return ResponseEntity.status(HttpStatus.OK).body(
                new ApiResponse<>(true, "User cart cleared successfully", null)
        );
    }
}
