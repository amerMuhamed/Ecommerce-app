package com.spring.eCommerce.Controller;

import com.spring.eCommerce.dto.api.ApiResponse;
import com.spring.eCommerce.dto.payment.PaymentInitiateRequest;
import com.spring.eCommerce.service.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/orders/{orderId}/initiate")
    public ResponseEntity<ApiResponse<?>> initiatePayment(
            @PathVariable Long orderId,
            @RequestBody(required = false) PaymentInitiateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new ApiResponse<>(true, "Payment initiated successfully",
                        paymentService.initiatePayment(orderId, request))
        );
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<?>> getPaymentByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Payment retrieved successfully",
                        paymentService.getPaymentByOrderId(orderId))
        );
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<?>> getMyPayments() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Payments retrieved successfully", paymentService.getMyPayments())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getPaymentById(@PathVariable Long id) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Payment retrieved successfully", paymentService.getPaymentById(id))
        );
    }
}
