package com.spring.eCommerce.Mapper;

import com.spring.eCommerce.dto.product.ProductRequestDto;
import com.spring.eCommerce.dto.product.ProductResponseDto;
import com.spring.eCommerce.entity.Product;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductResponseDto toDto(Product product);

    Product toEntity(ProductRequestDto dto);

}