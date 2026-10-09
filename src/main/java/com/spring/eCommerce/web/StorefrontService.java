package com.spring.eCommerce.web;

import com.spring.eCommerce.dto.product.ProductResponseDto;
import com.spring.eCommerce.security.AppUserDetail;
import com.spring.eCommerce.service.category.CategoryService;
import com.spring.eCommerce.service.product.ProductService;
import com.spring.eCommerce.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read-only composition helpers for the Thymeleaf storefront.
 * All business logic stays in the existing services; this class only
 * shapes their output for views.
 */
@Service
@RequiredArgsConstructor
public class StorefrontService {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final UserService userService;

    public List<ProductResponseDto> allProducts() {
        try {
            return productService.getAll();
        } catch (Exception e) {
            return List.of();
        }
    }

    public boolean isAdmin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserDetail details)) {
            return false;
        }
        return details.getAuthorities().stream()
                .anyMatch(a -> "admin".equals(a.getAuthority()));
    }

    public int cartItemCount() {
        try {
            var cart = userService.getCurrentUser() != null
                    ? userService.getCurrentUser().getCart() : null;
            if (cart == null || cart.getCartItems() == null) {
                return 0;
            }
            return cart.getCartItems().stream().mapToInt(i -> i.getQuantity()).sum();
        } catch (Exception e) {
            return 0;
        }
    }
}
