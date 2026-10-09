package com.spring.eCommerce.web.controller;

import com.spring.eCommerce.dto.order.OrderResponseDto;
import com.spring.eCommerce.dto.payment.PaymentInitiateRequest;
import com.spring.eCommerce.dto.payment.PaymentResponseDto;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.service.cart.CartService;
import com.spring.eCommerce.service.order.OrderService;
import com.spring.eCommerce.service.payment.PaymentService;
import com.spring.eCommerce.service.product.ProductService;
import com.spring.eCommerce.web.StorefrontService;
import com.spring.eCommerce.web.controller.CartWebController.CartLine;
import com.spring.eCommerce.web.dto.CheckoutForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Browser checkout and order history, reusing the existing order
 * creation flow and Paymob payment initiation.
 */
@Controller
@RequiredArgsConstructor
public class OrderWebController {

    private final OrderService orderService;
    private final CartService cartService;
    private final ProductService productService;
    private final PaymentService paymentService;
    private final StorefrontService storefront;

    @GetMapping("/checkout")
    public String checkoutForm(Model model, Authentication authentication) {
        List<CartLine> lines = loadLines();
        if (lines.isEmpty()) {
            return "redirect:/cart";
        }
        if (!model.containsAttribute("checkoutForm")) {
            model.addAttribute("checkoutForm", new CheckoutForm());
        }
        model.addAttribute("lines", lines);
        model.addAttribute("subtotal", subtotal(lines));
        model.addAttribute("cartCount", storefront.cartItemCount());
        model.addAttribute("isAdmin", storefront.isAdmin(authentication));
        return "checkout";
    }

    @PostMapping("/checkout")
    public String placeOrder(@Valid @ModelAttribute("checkoutForm") CheckoutForm form,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {
        List<CartLine> lines = loadLines();
        if (lines.isEmpty()) {
            return "redirect:/cart";
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.checkoutForm", bindingResult);
            redirectAttributes.addFlashAttribute("checkoutForm", form);
            return "redirect:/checkout";
        }
        OrderResponseDto order;
        try {
            order = orderService.createOrder(form.getShippingAddress().trim());
        } catch (IllegalStateException | BusinessException e) {
            redirectAttributes.addFlashAttribute("checkoutForm", form);
            redirectAttributes.addFlashAttribute("checkoutError",
                    e.getMessage() == null ? "Could not place your order." : e.getMessage());
            return "redirect:/checkout";
        }
        if ("paymob".equalsIgnoreCase(form.getPaymentMethod())) {
            try {
                PaymentResponseDto payment = paymentService.initiatePayment(order.id(),
                        new PaymentInitiateRequest(PaymentProvider.PAYMOB,
                                "web-" + order.id() + "-" + UUID.randomUUID(),
                                form.getPhone(), null));
                if (payment.checkoutUrl() != null && !payment.checkoutUrl().isBlank()) {
                    return "redirect:" + payment.checkoutUrl();
                }
                redirectAttributes.addFlashAttribute("orderNotice",
                        "Order placed. Continue to payment from your order details.");
            } catch (BusinessException e) {
                redirectAttributes.addFlashAttribute("orderNotice",
                        "Order placed. Online payment could not start ("
                                + (e.getMessage() == null ? "try again" : e.getMessage())
                                + "); you can pay on delivery.");
            }
        }
        return "redirect:/orders/" + order.id() + "?placed";
    }

    @GetMapping("/orders")
    public String myOrders(Model model, Authentication authentication) {
        List<OrderResponseDto> orders;
        try {
            orders = orderService.getMyOrders();
        } catch (Exception e) {
            orders = List.of();
            model.addAttribute("ordersError", "We could not load your orders right now.");
        }
        model.addAttribute("orders", orders);
        model.addAttribute("cartCount", storefront.cartItemCount());
        model.addAttribute("isAdmin", storefront.isAdmin(authentication));
        return "orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetails(@PathVariable Long id, Model model, Authentication authentication) {
        try {
            OrderResponseDto order = orderService.getOrderById(id);
            model.addAttribute("order", order);
            try {
                model.addAttribute("payment", paymentService.getPaymentByOrderId(id));
            } catch (Exception ignored) {
                // Orders without a payment (e.g. cash on delivery) simply show no payment block.
            }
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", "Order not found.");
            model.addAttribute("cartCount", storefront.cartItemCount());
            model.addAttribute("isAdmin", storefront.isAdmin(authentication));
            return "error";
        }
        model.addAttribute("cartCount", storefront.cartItemCount());
        model.addAttribute("isAdmin", storefront.isAdmin(authentication));
        return "order-details";
    }

    @PostMapping("/orders/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            orderService.cancelOrder(id);
            redirectAttributes.addFlashAttribute("orderNotice", "Order cancelled.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("orderNotice",
                    e.getMessage() == null ? "Could not cancel this order." : e.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    private List<CartLine> loadLines() {
        var cart = cartService.getCart();
        if (cart == null || cart.cartItems() == null) {
            return List.of();
        }
        List<CartLine> lines = new ArrayList<>();
        cart.cartItems().forEach(item -> {
            try {
                var product = productService.getById(item.productId());
                String image = (product.images() != null && !product.images().isEmpty())
                        ? product.images().get(0).imageUrl() : null;
                lines.add(new CartLine(product.id(), product.name(), image,
                        product.price(), item.quantity(),
                        product.price().multiply(BigDecimal.valueOf(item.quantity())),
                        product.availableQuantity()));
            } catch (Exception ignored) {
            }
        });
        return lines;
    }

    private BigDecimal subtotal(List<CartLine> lines) {
        return lines.stream().map(CartLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
