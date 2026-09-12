package com.spring.eCommerce.service.category;

import com.spring.eCommerce.dto.category.CategoryRequestDto;
import com.spring.eCommerce.dto.category.CategoryResponseDto;
import com.spring.eCommerce.service.comman.CommonService;

import java.util.List;

public interface CategoryService extends CommonService<CategoryRequestDto, CategoryResponseDto> {
    void deleteByName(String name);

    CategoryResponseDto getByName(String name);

    void addProducts(Long categoryId, List<Long> productIds);
}
