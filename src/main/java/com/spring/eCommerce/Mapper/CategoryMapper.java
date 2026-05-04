package com.spring.eCommerce.Mapper;

import com.spring.eCommerce.dto.category.CategoryRequestDto;
import com.spring.eCommerce.dto.category.CategoryResponseDto;
import com.spring.eCommerce.entity.Category;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponseDto toDto(Category category);

    Category toEntity(CategoryRequestDto categoryRequestDto);
}
