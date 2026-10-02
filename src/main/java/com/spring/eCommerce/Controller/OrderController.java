package com.spring.eCommerce.Controller;

import com.spring.eCommerce.dto.api.ApiResponse;
import com.spring.eCommerce.dto.order.OrderRequestDto;
import com.spring.eCommerce.service.order.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<?>> createOrder(@Valid @RequestBody OrderRequestDto orderRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new ApiResponse<>(true, "Order created successfully", orderService.createOrder(orderRequestDto.shippingAddress()))
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getMyOrders() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Orders retrieved successfully", orderService.getMyOrders())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Order retrieved successfully", orderService.getOrderById(id))
        );
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<?>> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Order cancelled successfully", orderService.cancelOrder(id))
        );
    }
}
