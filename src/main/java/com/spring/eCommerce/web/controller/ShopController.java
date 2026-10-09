package com.spring.eCommerce.web.controller;

import com.spring.eCommerce.dto.category.CategoryResponseDto;
import com.spring.eCommerce.dto.product.ProductResponseDto;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.service.category.CategoryService;
import com.spring.eCommerce.service.product.ProductService;
import com.spring.eCommerce.web.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Public product catalog and product details.
 */
@Controller
@RequiredArgsConstructor
public class ShopController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final StorefrontService storefront;

    @GetMapping("/shop")
    public String shop(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long category,
            @RequestParam(required = false, defaultValue = "featured") String sort,
            @RequestParam(required = false, defaultValue = "1") int page,
            Model model,
            Authentication authentication) {
        List<CategoryResponseDto> categories;
        try {
            categories = categoryService.getAll();
        } catch (Exception e) {
            categories = List.of();
        }
        List<ProductResponseDto> products;
        try {
            products = new ArrayList<>(productService.getAll());
        } catch (Exception e) {
            products = new ArrayList<>();
            model.addAttribute("shopError", "We could not load products right now. Please try again later.");
        }

        if (q != null && !q.isBlank()) {
            String needle = q.trim().toLowerCase();
            products = products.stream()
                    .filter(p -> (p.name() != null && p.name().toLowerCase().contains(needle))
                            || (p.description() != null && p.description().toLowerCase().contains(needle)))
                    .toList();
        }
        if (category != null) {
            products = products.stream()
                    .filter(p -> p.categories() != null
                            && p.categories().stream().anyMatch(c -> category.equals(c.id())))
                    .toList();
        }
        products = sort(products, sort);

        int pageSize = 12;
        int total = products.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        int safePage = Math.min(Math.max(page, 1), totalPages);
        List<ProductResponseDto> pageItems = products.stream()
                .skip((long) (safePage - 1) * pageSize)
                .limit(pageSize)
                .toList();

        model.addAttribute("categories", categories);
        model.addAttribute("products", pageItems);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("sort", sort);
        model.addAttribute("page", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("total", total);
        model.addAttribute("cartCount", storefront.cartItemCount());
        model.addAttribute("isAdmin", storefront.isAdmin(authentication));
        return "shop";
    }

    @GetMapping("/product/{id}")
    public String details(@PathVariable Long id, Model model, Authentication authentication) {
        ProductResponseDto product;
        try {
            product = productService.getById(id);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", "Sorry, this product could not be found.");
            model.addAttribute("cartCount", storefront.cartItemCount());
            model.addAttribute("isAdmin", storefront.isAdmin(authentication));
            return "error";
        }
        List<ProductResponseDto> related = List.of();
        try {
            List<Long> catIds = product.categories() == null ? List.of()
                    : product.categories().stream().map(CategoryResponseDto::id).toList();
            related = productService.getAll().stream()
                    .filter(p -> !p.id().equals(product.id()))
                    .filter(p -> p.categories() != null
                            && p.categories().stream().anyMatch(c -> catIds.contains(c.id())))
                    .limit(4)
                    .toList();
        } catch (Exception ignored) {
        }
        model.addAttribute("product", product);
        model.addAttribute("related", related);
        model.addAttribute("cartCount", storefront.cartItemCount());
        model.addAttribute("isAdmin", storefront.isAdmin(authentication));
        return "product-details";
    }

    private List<ProductResponseDto> sort(List<ProductResponseDto> products, String sort) {
        return switch (sort == null ? "featured" : sort) {
            case "price-asc" -> products.stream()
                    .sorted(Comparator.comparing(ProductResponseDto::price,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            case "price-desc" -> products.stream()
                    .sorted(Comparator.comparing(ProductResponseDto::price,
                            Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .toList();
            case "name-asc" -> products.stream()
                    .sorted(Comparator.comparing(p -> p.name() == null ? "" : p.name().toLowerCase()))
                    .toList();
            case "name-desc" -> products.stream()
                    .sorted(Comparator.comparing((ProductResponseDto p) -> p.name() == null ? "" : p.name().toLowerCase())
                            .reversed())
                    .toList();
            default -> products;
        };
    }
}
