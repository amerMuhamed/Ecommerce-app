package com.spring.eCommerce.dto.category;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CategoryRequestDto(
        @NotBlank(message = "Category name is required")
        String name,
        List<Long> productIds
) {
}
