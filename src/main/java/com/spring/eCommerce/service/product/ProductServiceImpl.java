package com.spring.eCommerce.service.product;


import com.spring.eCommerce.Mapper.ProductMapper;
import com.spring.eCommerce.dto.product.ProductRequestDto;
import com.spring.eCommerce.dto.product.ProductResponseDto;
import com.spring.eCommerce.entity.Product;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.repository.CartItemRepo;
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

    private final CartItemRepo cartItemRepo;


    @Override
    public List<ProductResponseDto> getAll() {
        return productRepo.findAllByDeletedFalse().stream().map(productMapper::toDto).toList();
    }

    @Override
    public ProductResponseDto getById(Long id) {
        return productRepo.findByIdAndDeletedFalse(id)
                .map(productMapper::toDto)
                .orElseThrow(() -> new BusinessException("Product not found with id: " + id));
    }

    public ProductResponseDto getByName(String name) {
        Product product = productRepo.findByNameAndDeletedFalse(name);
        return product == null ? null : productMapper.toDto(product);
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
    @Transactional
    public void deleteByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Product name must not be null or empty for deletion.");
        }
        Product productToDelete = productRepo.findByNameAndDeletedFalse(name);
        if (productToDelete == null) {
            throw new BusinessException("Product not found with name: " + name);
        }
        softDelete(productToDelete);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Product ID must not be null for deletion.");
        }
        Product productToDelete = productRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("Product not found with id: " + id));
        softDelete(productToDelete);
    }

    /**
     * Products are referenced by order items, so they are flagged as deleted instead of removed.
     * The product disappears from the shop, its categories and every cart; past orders still show it.
     */
    private void softDelete(Product product) {
        product.setDeleted(true);
        product.getCategories().clear();
        cartItemRepo.deleteByProductId(product.getId());
        productRepo.save(product);
    }

    @Override
    @Transactional
    public ProductResponseDto update(Long id, ProductRequestDto obj) {

        if (id == null) {
            throw new IllegalArgumentException("Product ID must not be null for update.");
        }

        Product existingProduct = productRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("Product not found with ID: " + id));
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
