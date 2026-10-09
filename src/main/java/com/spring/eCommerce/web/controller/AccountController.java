package com.spring.eCommerce.web.controller;

import com.spring.eCommerce.dto.order.OrderResponseDto;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.service.order.OrderService;
import com.spring.eCommerce.service.user.UserService;
import com.spring.eCommerce.web.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Customer account page: profile details plus order history.
 */
@Controller
@RequiredArgsConstructor
public class AccountController {

    private final UserService userService;
    private final OrderService orderService;
    private final StorefrontService storefront;

    @GetMapping("/account")
    public String account(Model model, Authentication authentication) {
        AppUser user;
        try {
            user = userService.getCurrentUser();
        } catch (Exception e) {
            return "redirect:/login?required";
        }
        List<OrderResponseDto> orders;
        try {
            orders = orderService.getMyOrders();
        } catch (Exception e) {
            orders = List.of();
        }
        model.addAttribute("user", user);
        model.addAttribute("orders", orders);
        model.addAttribute("cartCount", storefront.cartItemCount());
        model.addAttribute("isAdmin", storefront.isAdmin(authentication));
        return "account";
    }
}
