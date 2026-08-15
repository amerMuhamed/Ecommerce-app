package com.spring.eCommerce.service.category;

import com.spring.eCommerce.Mapper.CategoryMapper;
import com.spring.eCommerce.dto.category.CategoryRequestDto;
import com.spring.eCommerce.dto.category.CategoryResponseDto;
import com.spring.eCommerce.entity.Category;
import com.spring.eCommerce.entity.Product;
import com.spring.eCommerce.repository.CategoryRepo;
import com.spring.eCommerce.repository.ProductRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final CategoryRepo categoryRepo;
    private final ProductRepo productRepo;

    @Override
    public List<CategoryResponseDto> getAll() {
        return categoryRepo.findAll()
                .stream()
                .map(categoryMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryResponseDto getById(Long id) {
        return categoryRepo.findById(id)
                .map(categoryMapper::toDto)
                .orElse(null);
    }

    public CategoryResponseDto getByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name must not be null or empty.");
        }
        return categoryMapper.toDto(categoryRepo.findByName(name));
    }

    @Override
    @Transactional
    public CategoryResponseDto save(CategoryRequestDto obj) {
        Category savedCategory = categoryRepo.save(categoryMapper.toEntity(obj));

        if (obj.productIds() != null && !obj.productIds().isEmpty()) {
            List<Product> products = productRepo.findAllById(obj.productIds());
            products.forEach(product -> product.getCategories().add(savedCategory));
            productRepo.saveAll(products);
        }

        return categoryMapper.toDto(savedCategory);
    }

    @Override
    @Transactional
    public void delete(CategoryRequestDto obj) {
        if (obj == null || obj.name() == null || obj.name().trim().isEmpty()) {
            throw new IllegalArgumentException("Category object must not be null for deletion.");
        }
        Category categoryToDelete = categoryRepo.findByName(obj.name());
        if (categoryToDelete == null) {
            throw new RuntimeException("Category not found with name: " + obj.name());
        }
        categoryRepo.unlinkProducts(categoryToDelete.getId());
        categoryRepo.deleteById(categoryToDelete.getId());
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        categoryRepo.unlinkProducts(id);
        categoryRepo.deleteById(id);
    }

    @Override
    public CategoryResponseDto update(Long id, CategoryRequestDto obj) {
        if (id == null) {
            throw new IllegalArgumentException("Category ID must not be null for update.");
        }
        Category existingCategory = categoryRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with ID: " + id));
        if (obj.name() != null) {
        existingCategory.setName(obj.name());
        }
        return categoryMapper.toDto(categoryRepo.save(existingCategory));
    }
}
