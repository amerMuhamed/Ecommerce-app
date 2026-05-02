package com.spring.eCommerce.dto;

import java.util.Date;
import java.util.List;

public record ProductDto(
        Long id,
        String name,
        String description,
        Double price,
        List<ImageDto> images,
        int availableQuantity,
        Date createdDate,
        List<CategoryDto> categories
) {
}