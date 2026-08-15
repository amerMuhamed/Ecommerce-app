package com.spring.eCommerce.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record ProductRequestDto(
        @NotBlank(message = "Product name is required")
        String name,

        String description,

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be greater than zero")
        Double price,

        @NotNull(message = "Available quantity is required")
        @PositiveOrZero(message = "Available quantity cannot be negative")
        Integer availableQuantity,
        List<Long> categoryIds
) {
}
