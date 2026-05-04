package com.spring.eCommerce.dto.product;

import com.spring.eCommerce.dto.category.CategoryResponseDto;
import com.spring.eCommerce.dto.image.ImageResponseDto;

import java.util.Date;
import java.util.List;

public record ProductResponseDto(
        Long id,
        String name,
        String description,
        Double price,
        List<ImageResponseDto> images,
        int availableQuantity,
        Date createdDate,
        List<CategoryResponseDto> categories

) {
}
