package com.spring.eCommerce.Controller;

import com.spring.eCommerce.dto.api.ApiResponse;
import com.spring.eCommerce.service.role.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor

public class RoleController {
    private final RoleService roleService;

    @GetMapping("")
    public ResponseEntity<ApiResponse<?>> findAll() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Roles retrieved successfully", roleService.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> findById(@PathVariable Long id) {
        var role = roleService.findById(id);
        if (role == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, "Role not found", null));
        }
        return ResponseEntity.ok(new ApiResponse<>(true, "Role retrieved successfully", role));
    }
}
