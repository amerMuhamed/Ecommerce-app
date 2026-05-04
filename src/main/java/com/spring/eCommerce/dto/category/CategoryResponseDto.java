package com.spring.eCommerce.dto.category;

import jakarta.validation.constraints.NotBlank;

public record CategoryResponseDto(
        Long id,
        @NotBlank(message = "Category name is required")
        String name
) {
}