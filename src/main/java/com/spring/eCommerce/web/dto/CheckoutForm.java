package com.spring.eCommerce.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckoutForm {

    @NotBlank(message = "Shipping address is required")
    private String shippingAddress;

    private String phone;

    @Email(message = "Enter a valid email address")
    private String email;

    /** "cod" (default) or "paymob". */
    private String paymentMethod = "cod";
}
