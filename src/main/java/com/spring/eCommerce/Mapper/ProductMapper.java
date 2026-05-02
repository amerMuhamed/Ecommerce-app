package com.spring.eCommerce.Mapper;

import com.spring.eCommerce.dto.ProductDto;
import com.spring.eCommerce.entity.Product;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductDto toDto(Product product);

    Product toEntity(ProductDto dto);
}