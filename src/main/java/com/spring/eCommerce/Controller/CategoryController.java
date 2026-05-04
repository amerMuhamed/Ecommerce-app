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

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> delete(@PathVariable Long id) {
        categoryService.deleteById(id);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Category deleted successfully", null)
        );
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> patch(
            @PathVariable Long id,
            @RequestBody CategoryRequestDto categoryRequestDto
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Category updated successfully",
                        categoryService.update(id, categoryRequestDto)
                )
        );
    }
}