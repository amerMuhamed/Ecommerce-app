package com.spring.eCommerce.Mapper;

import com.spring.eCommerce.dto.CategoryDto;
import com.spring.eCommerce.entity.Category;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryDto toDto(Category category);

    Category toEntity(CategoryDto categoryDto);
}
