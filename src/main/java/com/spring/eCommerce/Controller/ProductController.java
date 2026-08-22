package com.spring.eCommerce.Controller;

import com.spring.eCommerce.dto.api.ApiResponse;
import com.spring.eCommerce.dto.product.ProductRequestDto;
import com.spring.eCommerce.service.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Product retrieved successfully", productService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getAll() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Products retrieved successfully", productService.getAll()));
    }

    @GetMapping("/name")
    public ResponseEntity<ApiResponse<?>> getByName(@RequestParam String name) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Product retrieved successfully", productService.getByName(name)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> save(@RequestBody ProductRequestDto productRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new ApiResponse<>(true, "Product created successfully", productService.save(productRequestDto))
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> update(@PathVariable Long id, @RequestBody ProductRequestDto productRequestDto) {
        return ResponseEntity.status(HttpStatus.OK).body(
                new ApiResponse<>(true, "Product updated successfully", productService.update(id, productRequestDto))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> deleteById(@PathVariable Long id) {
        productService.deleteById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Product deleted successfully", null));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<?>> deleteByName(@RequestParam String name) {
        productService.deleteByName(name);
        return ResponseEntity.ok(new ApiResponse<>(true, "Product deleted successfully", null));
    }
}
