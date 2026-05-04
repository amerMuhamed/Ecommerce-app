package com.spring.eCommerce.Controller;

import com.spring.eCommerce.dto.api.ApiResponse;
import com.spring.eCommerce.dto.user.UpdateUserRequest;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor

public class UserController {
    private final UserService userService;
    @GetMapping("")
    public ResponseEntity<ApiResponse<?>> findAllUsers() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Users retrieved successfully", userService.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> findUserById(@PathVariable Long id) {
        AppUser user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, "User not found", null));
        }
        return ResponseEntity.ok(new ApiResponse<>(true, "User retrieved successfully", user));
    }

    @PutMapping("/update")
    public ResponseEntity<ApiResponse<?>> updateCurrentUser(@RequestBody UpdateUserRequest updateUserRequest, @AuthenticationPrincipal UserDetails userDetails) {
        AppUser currentUser = userService.findByUserName(userDetails.getUsername());
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, "User not found", null));
        }

        try {
            AppUser savedUser = userService.updateUser(currentUser.getId(), updateUserRequest);
            return ResponseEntity.ok(new ApiResponse<>(true, "User updated successfully", savedUser));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @PostMapping("/Upload-profile-image")
    public ResponseEntity<ApiResponse<?>> uploadProfileImage(@RequestParam("image") MultipartFile image, @AuthenticationPrincipal UserDetails userDetails) {
        AppUser user = userService.findByUserName(userDetails.getUsername());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, "User not found", null));
        }
        AppUser updatedUser = userService.uploadProfileImage(user, image);
        return ResponseEntity.ok(new ApiResponse<>(true, "Image uploaded successfully", updatedUser.getImage().getImageUrl()));
    }

    @Transactional
    @DeleteMapping("/delete-profile-image")
    public ResponseEntity<ApiResponse<?>> deleteProfileImage(@AuthenticationPrincipal UserDetails userDetails) {
        AppUser user = userService.findByUserName(userDetails.getUsername());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, "User not found", null));
        }
        String result = userService.deleteProfileImage(user);
        return ResponseEntity.ok(new ApiResponse<>(true, "Profile image deleted successfully", result));
    }


}
