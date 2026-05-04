package com.spring.eCommerce.Controller;

import com.spring.eCommerce.dto.*;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.service.authentication.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@RequestBody LoginRequest loginRequest) {
        JWTResponse jwtResponse = authService.login(loginRequest.username(), loginRequest.password());
        return ResponseEntity.ok(new ApiResponse<>(true, "Login successful", jwtResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, "Missing or invalid Authorization header", null));
        }
        String accessToken = authHeader.substring("Bearer ".length());
        authService.logout(accessToken);
        return ResponseEntity.ok(new ApiResponse<>(true, "Logged out successfully", null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<?>> refresh(@RequestBody RefreshTokenRequest request) {
        JWTResponse jwtResponse = authService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(new ApiResponse<>(true, "Token refreshed successfully", jwtResponse));
    }

    @PostMapping("/registerUser")
    public ResponseEntity<ApiResponse<?>> registerUser(@Valid @RequestBody UserRegistrationDto userDto, BindingResult result) {
        if (result.hasErrors()) {
            String errors = result.getAllErrors().stream()
                    .map(ObjectError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, errors, null));
        }
        AppUser appUser = new AppUser();
        appUser.setUsername(userDto.getUsername());
        appUser.setPassword(userDto.getPassword());
        appUser.setFullName(userDto.getFullName());

        authService.registerAsUser(appUser);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "User registered successfully", null));
    }

    @PostMapping("/registerAdmin")
    public ResponseEntity<ApiResponse<?>> CreateAdmin(@Valid @RequestBody UserRegistrationDto userDto, BindingResult result) {
        if (result.hasErrors()) {
            String errors = result.getAllErrors().stream()
                    .map(ObjectError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, errors, null));
        }
        AppUser appUser = new AppUser();
        appUser.setUsername(userDto.getUsername());
        appUser.setPassword(userDto.getPassword());
        appUser.setFullName(userDto.getFullName());
        authService.registerAsAdmin(appUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "User saved successfully", null));
    }

}
