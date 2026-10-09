package com.spring.eCommerce.web.controller;

import com.spring.eCommerce.dto.cart.CartItemRequestDto;
import com.spring.eCommerce.dto.cart.CartItemResponseDto;
import com.spring.eCommerce.dto.cart.CartResponseDto;
import com.spring.eCommerce.dto.product.ProductResponseDto;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.service.cart.CartService;
import com.spring.eCommerce.service.product.ProductService;
import com.spring.eCommerce.web.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Browser shopping cart, backed by the existing {@link CartService}.
 */
@Controller
@RequiredArgsConstructor
public class CartWebController {

    private final CartService cartService;
    private final ProductService productService;
    private final StorefrontService storefront;

    public record CartLine(Long productId, String name, String imageUrl,
                           BigDecimal price, int quantity, BigDecimal lineTotal, int stock) {
    }

    @GetMapping("/cart")
    public String view(Model model, Authentication authentication) {
        List<CartLine> lines = loadLines();
        model.addAttribute("lines", lines);
        model.addAttribute("subtotal", subtotal(lines));
        model.addAttribute("cartCount", storefront.cartItemCount());
        model.addAttribute("isAdmin", storefront.isAdmin(authentication));
        return "cart";
    }

    @PostMapping("/cart/add")
    public String add(@RequestParam Long productId,
                      @RequestParam(defaultValue = "1") int quantity,
                      RedirectAttributes redirectAttributes) {
        int qty = Math.max(1, quantity);
        try {
            cartService.addItemToCart(new CartItemRequestDto(productId, qty));
            redirectAttributes.addFlashAttribute("cartMessage", "Added to your cart.");
        } catch (BusinessException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("cartError",
                    e.getMessage() == null ? "Could not add this item to your cart." : e.getMessage());
        }
        return "redirect:/product/" + productId;
    }

    @PostMapping("/cart/update")
    public String update(@RequestParam Long productId,
                         @RequestParam int quantity,
                         RedirectAttributes redirectAttributes) {
        try {
            cartService.updateItemQuantity(productId, quantity);
        } catch (BusinessException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("cartError",
                    e.getMessage() == null ? "Could not update quantity." : e.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String remove(@RequestParam Long productId) {
        try {
            cartService.removeItem(productId);
        } catch (BusinessException | IllegalStateException ignored) {
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/clear")
    public String clear() {
        cartService.clearCart();
        return "redirect:/cart";
    }

    private List<CartLine> loadLines() {
        CartResponseDto cart;
        try {
            cart = cartService.getCart();
        } catch (Exception e) {
            return List.of();
        }
        if (cart == null || cart.cartItems() == null) {
            return List.of();
        }
        List<CartLine> lines = new ArrayList<>();
        for (CartItemResponseDto item : cart.cartItems()) {
            try {
                ProductResponseDto product = productService.getById(item.productId());
                String image = (product.images() != null && !product.images().isEmpty())
                        ? product.images().get(0).imageUrl() : null;
                BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(item.quantity()));
                lines.add(new CartLine(product.id(), product.name(), image,
                        product.price(), item.quantity(), lineTotal, product.availableQuantity()));
            } catch (Exception ignored) {
            }
        }
        return lines;
    }

    private BigDecimal subtotal(List<CartLine> lines) {
        return lines.stream().map(CartLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
