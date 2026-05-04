package com.spring.eCommerce.dto.api;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data
) {
}