package com.spring.eCommerce.web.controller;

import com.spring.eCommerce.dto.product.ProductResponseDto;
import com.spring.eCommerce.service.category.CategoryService;
import com.spring.eCommerce.service.product.ProductService;
import com.spring.eCommerce.web.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;
import java.util.List;

/**
 * Public storefront home page.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final StorefrontService storefront;

    @GetMapping("/")
    public String home(Model model, Authentication authentication) {
        List<ProductResponseDto> products;
        try {
            products = productService.getAll();
        } catch (Exception e) {
            products = List.of();
        }
        List<ProductResponseDto> featured = products.stream().limit(8).toList();
        List<ProductResponseDto> arrivals = products.stream()
                .sorted(Comparator.comparing(ProductResponseDto::id,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(4)
                .toList();
        try {
            model.addAttribute("categories", categoryService.getAll());
        } catch (Exception e) {
            model.addAttribute("categories", List.of());
        }
        model.addAttribute("featuredProducts", featured);
        model.addAttribute("newArrivals", arrivals);
        model.addAttribute("cartCount", storefront.cartItemCount());
        model.addAttribute("isAdmin", storefront.isAdmin(authentication));
        return "home";
    }
}
