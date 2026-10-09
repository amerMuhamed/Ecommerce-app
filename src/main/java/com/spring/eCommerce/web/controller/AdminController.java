package com.spring.eCommerce.web.controller;

import com.spring.eCommerce.dto.product.ProductRequestDto;
import com.spring.eCommerce.dto.product.ProductResponseDto;
import com.spring.eCommerce.entity.Image;
import com.spring.eCommerce.entity.Order;
import com.spring.eCommerce.entity.Product;
import com.spring.eCommerce.entity.enums.OrderStatus;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.repository.OrderRepo;
import com.spring.eCommerce.repository.PaymentRepo;
import com.spring.eCommerce.repository.ProductRepo;
import com.spring.eCommerce.service.category.CategoryService;
import com.spring.eCommerce.service.image.ImageService;
import com.spring.eCommerce.service.product.ProductService;
import com.spring.eCommerce.web.AdminOrderService;
import com.spring.eCommerce.web.StorefrontService;
import com.spring.eCommerce.web.dto.ProductForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Admin-only product management and order visibility.
 * Every handler is restricted server-side to the {@code admin} authority;
 * the Thymeleaf UI additionally hides admin navigation from customers.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('admin')")
public class AdminController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final ProductRepo productRepo;
    private final OrderRepo orderRepo;
    private final PaymentRepo paymentRepo;
    private final ImageService imageService;
    private final AdminOrderService adminOrderService;
    private final StorefrontService storefront;

    @GetMapping
    public String dashboard(Model model, Authentication authentication) {
        List<ProductResponseDto> products;
        List<Order> orders;
        try {
            products = productService.getAll();
        } catch (Exception e) {
            products = List.of();
        }
        try {
            orders = orderRepo.findAll();
        } catch (Exception e) {
            orders = List.of();
        }
        long lowStock = products.stream().filter(p -> p.availableQuantity() <= 5).count();
        long pendingOrders = orders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.PENDING).count();
        List<Order> recent = orders.stream()
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .limit(5)
                .toList();
        model.addAttribute("productCount", products.size());
        model.addAttribute("lowStockCount", lowStock);
        model.addAttribute("lowStockProducts", products.stream()
                .filter(p -> p.availableQuantity() <= 5).limit(5).toList());
        model.addAttribute("orderCount", orders.size());
        model.addAttribute("pendingOrderCount", pendingOrders);
        model.addAttribute("recentOrders", recent);
        model.addAttribute("isAdmin", true);
        model.addAttribute("cartCount", storefront.cartItemCount());
        return "admin/dashboard";
    }

    @GetMapping("/products")
    public String products(Model model) {
        List<ProductResponseDto> products;
        try {
            products = productService.getAll();
        } catch (Exception e) {
            products = List.of();
            model.addAttribute("adminError", "Could not load products.");
        }
        model.addAttribute("products", products);
        model.addAttribute("isAdmin", true);
        model.addAttribute("cartCount", storefront.cartItemCount());
        return "admin/products";
    }

    @GetMapping("/products/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("productForm")) {
            model.addAttribute("productForm", new ProductForm());
        }
        model.addAttribute("categories", safeCategories());
        model.addAttribute("isAdmin", true);
        model.addAttribute("cartCount", storefront.cartItemCount());
        return "admin/product-form";
    }

    @PostMapping("/products")
    @Transactional
    public String create(@Valid @ModelAttribute("productForm") ProductForm form,
                         BindingResult bindingResult,
                         @RequestParam(required = false) MultipartFile imageFile,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", safeCategories());
            model.addAttribute("isAdmin", true);
            return "admin/product-form";
        }
        try {
            ProductResponseDto created = productService.save(new ProductRequestDto(
                    form.getName().trim(), form.getDescription(),
                    form.getPrice(), form.getAvailableQuantity(), form.getCategoryIds()));
            attachImage(created.id(), form.getImageUrl(), imageFile);
        } catch (BusinessException e) {
            model.addAttribute("categories", safeCategories());
            model.addAttribute("formError", e.getMessage());
            model.addAttribute("isAdmin", true);
            return "admin/product-form";
        } catch (Exception e) {
            model.addAttribute("categories", safeCategories());
            model.addAttribute("formError", "Could not save the product. Please try again.");
            model.addAttribute("isAdmin", true);
            return "admin/product-form";
        }
        redirectAttributes.addFlashAttribute("adminNotice", "Product created successfully.");
        return "redirect:/admin/products";
    }

    @GetMapping("/products/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        ProductResponseDto product;
        try {
            product = productService.getById(id);
        } catch (BusinessException e) {
            return "redirect:/admin/products";
        }
        if (!model.containsAttribute("productForm")) {
            ProductForm form = new ProductForm();
            form.setId(product.id());
            form.setName(product.name());
            form.setDescription(product.description());
            form.setPrice(product.price());
            form.setAvailableQuantity(product.availableQuantity());
            form.setCategoryIds(product.categories() == null ? List.of()
                    : product.categories().stream().map(c -> c.id()).toList());
            form.setImageUrl(product.images() == null || product.images().isEmpty()
                    ? "" : product.images().get(0).imageUrl());
            model.addAttribute("productForm", form);
        }
        model.addAttribute("categories", safeCategories());
        model.addAttribute("isAdmin", true);
        model.addAttribute("cartCount", storefront.cartItemCount());
        return "admin/product-form";
    }

    @PostMapping("/products/{id}/edit")
    @Transactional
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("productForm") ProductForm form,
                         BindingResult bindingResult,
                         @RequestParam(required = false) MultipartFile imageFile,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", safeCategories());
            model.addAttribute("isAdmin", true);
            return "admin/product-form";
        }
        try {
            productService.update(id, new ProductRequestDto(
                    form.getName().trim(), form.getDescription(),
                    form.getPrice(), form.getAvailableQuantity(), form.getCategoryIds()));
            attachImage(id, form.getImageUrl(), imageFile);
        } catch (BusinessException e) {
            model.addAttribute("categories", safeCategories());
            model.addAttribute("formError", e.getMessage());
            model.addAttribute("isAdmin", true);
            return "admin/product-form";
        } catch (Exception e) {
            model.addAttribute("categories", safeCategories());
            model.addAttribute("formError", "Could not update the product. Please try again.");
            model.addAttribute("isAdmin", true);
            return "admin/product-form";
        }
        redirectAttributes.addFlashAttribute("adminNotice", "Product updated successfully.");
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            productService.deleteById(id);
            redirectAttributes.addFlashAttribute("adminNotice", "Product deleted successfully.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("adminError",
                    e.getMessage() == null ? "Could not delete the product." : e.getMessage());
        }
        return "redirect:/admin/products";
    }

    @GetMapping("/orders")
    public String orders(Model model) {
        List<Order> orders;
        try {
            orders = orderRepo.findAll();
        } catch (Exception e) {
            orders = List.of();
            model.addAttribute("adminError", "Could not load orders.");
        }
        model.addAttribute("orders", orders);
        model.addAttribute("isAdmin", true);
        model.addAttribute("cartCount", storefront.cartItemCount());
        return "admin/orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetails(@PathVariable Long id, Model model) {
        try {
            Order order = adminOrderService.findById(id);
            model.addAttribute("order", order);
            model.addAttribute("orderDto", adminOrderService.toDto(order));
            model.addAttribute("statuses", OrderStatus.values());
            try {
                model.addAttribute("payments", paymentRepo.findByOrderId(id).map(List::of).orElse(List.of()));
            } catch (Exception ignored) {
                model.addAttribute("payments", List.of());
            }
        } catch (BusinessException e) {
            return "redirect:/admin/orders";
        }
        model.addAttribute("isAdmin", true);
        model.addAttribute("cartCount", storefront.cartItemCount());
        return "admin/order-details";
    }

    @PostMapping("/orders/{id}/status")
    public String changeStatus(@PathVariable Long id,
                               @RequestParam OrderStatus status,
                               RedirectAttributes redirectAttributes) {
        try {
            adminOrderService.changeStatus(id, status);
            redirectAttributes.addFlashAttribute("adminNotice", "Order status updated to " + status + ".");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("adminError",
                    e.getMessage() == null ? "Could not update order status." : e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    private List<?> safeCategories() {
        try {
            return categoryService.getAll();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Adds a product image from an uploaded file (Cloudinary) or a direct URL.
     * Reuses the existing image-storage mechanism; failures are non-fatal.
     */
    private void attachImage(Long productId, String imageUrl, MultipartFile imageFile) {
        try {
            Product product = productRepo.findById(productId).orElse(null);
            if (product == null) {
                return;
            }
            if (imageFile != null && !imageFile.isEmpty()) {
                Map<String, String> uploaded = imageService.uploadImage(imageFile);
                product.getImages().add(Image.builder()
                        .imageUrl(uploaded.get("imageUrl"))
                        .publicId(uploaded.get("publicId"))
                        .build());
                productRepo.save(product);
                return;
            }
            if (imageUrl != null && !imageUrl.isBlank()
                    && product.getImages().stream().noneMatch(i -> imageUrl.isBlank()
                            || imageUrl.trim().equals(i.getImageUrl()))) {
                product.getImages().add(Image.builder().imageUrl(imageUrl.trim()).build());
                productRepo.save(product);
            }
        } catch (Exception ignored) {
            // Image attachment must never fail product creation/update.
        }
    }
}
