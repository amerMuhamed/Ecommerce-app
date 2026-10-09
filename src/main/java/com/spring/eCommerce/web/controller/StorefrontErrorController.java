package com.spring.eCommerce.web.controller;

import com.spring.eCommerce.web.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Friendly error page for storefront navigation.
 */
@Controller
@RequiredArgsConstructor
public class StorefrontErrorController {

    private final StorefrontService storefront;

    @GetMapping("/error")
    public String error(@RequestParam(required = false) String forbidden, Model model,
                        Authentication authentication) {
        if (forbidden != null) {
            model.addAttribute("errorMessage", "You do not have permission to view this page.");
        } else if (!model.containsAttribute("errorMessage")) {
            model.addAttribute("errorMessage", "Something went wrong. Please try again.");
        }
        model.addAttribute("cartCount", storefront.cartItemCount());
        model.addAttribute("isAdmin", storefront.isAdmin(authentication));
        return "error";
    }
}
