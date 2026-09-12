package com.spring.eCommerce.service.product;

import com.spring.eCommerce.dto.product.ProductRequestDto;
import com.spring.eCommerce.dto.product.ProductResponseDto;
import com.spring.eCommerce.service.comman.CommonService;


public interface ProductService extends CommonService<ProductRequestDto, ProductResponseDto> {
    void deleteByName(String name);

    ProductResponseDto getByName(String name);

}
