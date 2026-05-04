package com.spring.eCommerce.service.product;

import com.spring.eCommerce.dto.product.ProductRequestDto;
import com.spring.eCommerce.dto.product.ProductResponseDto;
import com.spring.eCommerce.service.comman.CommonService;
import org.springframework.stereotype.Service;

@Service
public interface ProductService extends CommonService<ProductRequestDto, ProductResponseDto> {

    ProductResponseDto save(ProductRequestDto obj);
}
