package com.spring.eCommerce.service.product;


import com.spring.eCommerce.Mapper.ProductMapper;
import com.spring.eCommerce.dto.ProductDto;
import com.spring.eCommerce.entity.Product;
import com.spring.eCommerce.exception.NotFoundException;
import com.spring.eCommerce.repository.ProductRepo;
import com.spring.eCommerce.service.category.CategoryService;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor

public class ProductServiceImpl implements ProductService {

    private final ProductRepo productRepo;

    private final CategoryService categoryService;

    private final ProductMapper productMapper;


    @Override
    public List<ProductDto> getAll() {
        return productRepo.findAll().stream().map(productMapper::toDto).toList();
    }

    @Override
    public ProductDto getById(Long id) {
        return productRepo.findById(id)
                .map(productMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Product not found with id: " + id));
    }

    @Override
    public ProductDto save(ProductDto obj) {
        return productMapper.toDto(productRepo.save(productMapper.toEntity(obj)));
    }

    @Override
    public void delete(ProductDto obj) {
        productRepo.delete(productMapper.toEntity(obj));
    }

    @Override
    public void deleteById(Long id) {
        productRepo.deleteById(id);
    }

    @Override
    public ProductDto update(ProductDto obj) {

        if (obj.id() == null) {
            throw new IllegalArgumentException("Product ID must not be null for update.");
        }

        Product existingProduct = productRepo.findById(obj.id())
                .orElseThrow(() -> new RuntimeException("Product not found with ID: " + obj.id()));

        existingProduct.setName(obj.name());
        existingProduct.setDescription(obj.description());
        existingProduct.setPrice(obj.price());
        existingProduct.setAvailableQuantity(obj.availableQuantity());

        Product updatedProduct = productRepo.save(existingProduct);

        return productMapper.toDto(updatedProduct);
    }
}
