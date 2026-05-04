package com.spring.eCommerce.service.category;

import com.spring.eCommerce.dto.category.CategoryRequestDto;
import com.spring.eCommerce.dto.category.CategoryResponseDto;
import com.spring.eCommerce.service.comman.CommonService;
import org.springframework.stereotype.Service;

@Service
public interface CategoryService extends CommonService<CategoryRequestDto, CategoryResponseDto> {
}
