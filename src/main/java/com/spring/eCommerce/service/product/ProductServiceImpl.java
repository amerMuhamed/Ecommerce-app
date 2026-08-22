package com.spring.eCommerce.service.product;


import com.spring.eCommerce.Mapper.ProductMapper;
import com.spring.eCommerce.dto.product.ProductRequestDto;
import com.spring.eCommerce.dto.product.ProductResponseDto;
import com.spring.eCommerce.entity.Product;
import com.spring.eCommerce.exception.NotFoundException;
import com.spring.eCommerce.repository.CategoryRepo;
import com.spring.eCommerce.repository.ProductRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepo productRepo;

    private final ProductMapper productMapper;

    private final CategoryRepo categoryRepo;


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

    public ProductResponseDto getByName(String name) {
        if (productRepo.findByName(name) != null) {
            return productMapper.toDto(productRepo.findByName(name));
        }
        return null;
    }

    @Override
    @Transactional
    public ProductResponseDto save(ProductRequestDto obj) {
        Product product = productMapper.toEntity(obj);
        if (obj.categoryIds() != null && !obj.categoryIds().isEmpty()) {
            product.setCategories(categoryRepo.findAllById(obj.categoryIds()));
        }
        return productMapper.toDto(productRepo.save(product));
    }

    @Override
    public void deleteByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Product name must not be null or empty for deletion.");
        }
        Product productToDelete = productRepo.findByName(name);
        if (productToDelete == null) {
            throw new NotFoundException("Product not found with name: " + name);
        }
        productRepo.deleteById(productToDelete.getId());
    }

    @Override
    public void deleteById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Product ID must not be null for deletion.");
        }
        if (!productRepo.existsById(id)) {
            throw new NotFoundException("Product not found with id: " + id);
        }
        productRepo.deleteById(id);
    }

    @Override
    @Transactional
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

        if (obj.categoryIds() != null) {
            existingProduct.setCategories(categoryRepo.findAllById(obj.categoryIds()));
        }

        Product updatedProduct = productRepo.save(existingProduct);

        return productMapper.toDto(updatedProduct);
    }
}
