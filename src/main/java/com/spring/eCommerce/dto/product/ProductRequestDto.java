package com.spring.eCommerce.dto.product;

import com.spring.eCommerce.dto.category.CategoryResponseDto;
import com.spring.eCommerce.dto.image.ImageRequestDto;

import java.util.Date;
import java.util.List;

public record ProductRequestDto(

        String name,
        String description,
        Double price,
        List<ImageRequestDto> images,
        Integer availableQuantity,
        Date createdDate,
        List<CategoryResponseDto> categories
) {
}
