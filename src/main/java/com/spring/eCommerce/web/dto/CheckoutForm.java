package com.spring.eCommerce.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckoutForm {

    @NotBlank(message = "Shipping address is required")
    private String shippingAddress;

    private String phone;

    /** "cod" (default) or "paymob". */
    private String paymentMethod = "cod";
}
