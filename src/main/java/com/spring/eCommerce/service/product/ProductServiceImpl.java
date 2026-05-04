package com.spring.eCommerce.service.product;


import com.spring.eCommerce.Mapper.ProductMapper;
import com.spring.eCommerce.dto.product.ProductRequestDto;
import com.spring.eCommerce.dto.product.ProductResponseDto;
import com.spring.eCommerce.entity.Product;
import com.spring.eCommerce.exception.NotFoundException;
import com.spring.eCommerce.repository.ProductRepo;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor

public class ProductServiceImpl implements ProductService {

    private final ProductRepo productRepo;

    private final ProductMapper productMapper;


    @Override
    public List<ProductResponseDto> getAll() {
        return productRepo.findAll().stream().map(productMapper::toDto).toList();
    }

    @Override
    public ProductResponseDto getById(Long id) {
        return productRepo.findById(id)
                .map(productMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Product not found with id: " + id));
    }

    @Override
    public ProductResponseDto save(ProductRequestDto obj) {
        return productMapper.toDto(productRepo.save(productMapper.toEntity(obj)));
    }

    @Override
    public void delete(ProductRequestDto obj) {
        productRepo.delete(productMapper.toEntity(obj));
    }

    @Override
    public void deleteById(Long id) {
        productRepo.deleteById(id);
    }

    @Override
    public ProductResponseDto update(Long id, ProductRequestDto obj) {

        if (id == null) {
            throw new IllegalArgumentException("Product ID must not be null for update.");
        }

        Product existingProduct = productRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with ID: " + id));
        if (obj.name() != null) {
            existingProduct.setName(obj.name());
        }

        if (obj.description() != null) {
            existingProduct.setDescription(obj.description());
        }

        if (obj.price() != null) {
            existingProduct.setPrice(obj.price());
        }

        if (obj.availableQuantity() != null) {
            existingProduct.setAvailableQuantity(obj.availableQuantity());
        }

        Product updatedProduct = productRepo.save(existingProduct);

        return productMapper.toDto(updatedProduct);
    }
}
