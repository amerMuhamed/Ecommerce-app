package com.spring.eCommerce.service.category;

import com.spring.eCommerce.Mapper.CategoryMapper;
import com.spring.eCommerce.dto.CategoryDto;
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
    public List<CategoryDto> getAll() {
        return categoryRepo.findAll()
                .stream()
                .map(categoryMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryDto getById(Long id) {
        return categoryRepo.findById(id)
                .map(categoryMapper::toDto)
                .orElse(null);
    }

    @Override
    public CategoryDto save(CategoryDto obj) {
        return categoryMapper.toDto(categoryRepo.save(categoryMapper.toEntity(obj)));
    }

    @Override
    public void delete(CategoryDto obj) {
        categoryRepo.delete(categoryMapper.toEntity(obj));
    }

    @Override
    public void deleteById(Long id) {
        categoryRepo.deleteById(id);
    }

    @Override
    public CategoryDto update(CategoryDto obj) {
        if (obj.id() == null) {
            throw new IllegalArgumentException("Category ID must not be null for update.");
        }
        Category existingCategory = categoryRepo.findById(obj.id())
                .orElseThrow(() -> new RuntimeException("Category not found with ID: " + obj.id()));
        existingCategory.setName(obj.name());
        return categoryMapper.toDto(categoryRepo.save(existingCategory));
    }
}
