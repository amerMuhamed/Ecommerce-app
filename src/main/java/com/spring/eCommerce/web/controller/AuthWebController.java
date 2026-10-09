package com.spring.eCommerce.web.controller;

import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.service.authentication.AuthService;
import com.spring.eCommerce.web.dto.RegisterForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Browser login/registration pages. Registration always creates a
 * {@code user} customer; admin creation stays restricted to the secured API.
 */
@Controller
@RequiredArgsConstructor
public class AuthWebController {

    private final AuthService authService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RegisterForm());
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.form", bindingResult);
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/register";
        }
        AppUser appUser = new AppUser();
        appUser.setUsername(form.getUsername().trim());
        appUser.setPassword(form.getPassword());
        appUser.setFullName(form.getFullName().trim());
        try {
            authService.registerAsUser(appUser);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("form", form);
            redirectAttributes.addFlashAttribute("registerError", e.getMessage());
            return "redirect:/register";
        }
        redirectAttributes.addFlashAttribute("registered", true);
        return "redirect:/login?registered";
    }
}
