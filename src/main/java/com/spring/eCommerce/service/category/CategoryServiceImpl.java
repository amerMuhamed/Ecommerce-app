package com.spring.eCommerce.service.category;

import com.spring.eCommerce.Mapper.CategoryMapper;
import com.spring.eCommerce.dto.category.CategoryRequestDto;
import com.spring.eCommerce.dto.category.CategoryResponseDto;
import com.spring.eCommerce.entity.Category;
import com.spring.eCommerce.repository.CategoryRepo;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final CategoryRepo categoryRepo;

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

    @Override
    public CategoryResponseDto save(CategoryRequestDto obj) {
        return categoryMapper.toDto(categoryRepo.save(categoryMapper.toEntity(obj)));
    }

    @Override
    public void delete(CategoryRequestDto obj) {
        categoryRepo.delete(categoryMapper.toEntity(obj));
    }

    @Override
    public void deleteById(Long id) {
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
