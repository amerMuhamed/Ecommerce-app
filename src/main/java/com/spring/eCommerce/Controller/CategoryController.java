package com.spring.eCommerce.Controller;

import com.spring.eCommerce.dto.api.ApiResponse;
import com.spring.eCommerce.dto.category.CategoryRequestDto;
import com.spring.eCommerce.service.category.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getAll() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Categories retrieved successfully", categoryService.getAll())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Category retrieved successfully", categoryService.getById(id))
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> save(@Valid @RequestBody CategoryRequestDto categoryRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new ApiResponse<>(true, "Category created successfully", categoryService.save(categoryRequestDto))
        );
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> update(
            @PathVariable Long id,
            @RequestBody CategoryRequestDto categoryRequestDto
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Category updated successfully", categoryService.update(id, categoryRequestDto))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> deleteById(@PathVariable Long id) {
        categoryService.deleteById(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Category deleted successfully", null)
        );
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<?>> deleteByName(@Valid @RequestParam String name) {
        categoryService.deleteByName(name);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Category deleted successfully", null)
        );
    }
}