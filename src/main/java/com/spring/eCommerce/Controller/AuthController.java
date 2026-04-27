package com.spring.eCommerce.Controller;

import com.spring.eCommerce.dto.JWTResponse;
import com.spring.eCommerce.dto.LoginRequest;
import com.spring.eCommerce.dto.RefreshTokenRequest;
import com.spring.eCommerce.dto.UserRegistrationDto;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
    public ResponseEntity<JWTResponse> login(@RequestBody LoginRequest loginRequest) {
        JWTResponse jwtResponse = authService.login(loginRequest.username(), loginRequest.password());
        return ResponseEntity.ok(jwtResponse); }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body("Missing or invalid Authorization header");
        }
        String accessToken = authHeader.substring("Bearer ".length());
        authService.logout(accessToken);
        return ResponseEntity.ok("Logged out successfully");
    }

    @PostMapping("/refresh")
    public ResponseEntity<JWTResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshAccessToken(request.refreshToken()));
    }

    @PostMapping("/registerUser")
    public ResponseEntity<String> registerUser(@Valid @RequestBody UserRegistrationDto userDto, BindingResult result) {
        if (result.hasErrors()) {
            String errors = result.getAllErrors().stream()
                    .map(ObjectError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            return ResponseEntity.badRequest().body(errors);
        }
        AppUser appUser = new AppUser();
        appUser.setUsername(userDto.getUsername());
        appUser.setPassword(userDto.getPassword());
        appUser.setFullName(userDto.getFullName());

        authService.registerAsUser(appUser);

        return ResponseEntity.ok("User registered successfully");
    }
    @PostMapping("/registerAdmin")
    public ResponseEntity<String>CreateAdmin(@Valid @RequestBody UserRegistrationDto userDto, BindingResult result) {
        if (result.hasErrors()) {

            String errors = result.getAllErrors().stream()
                    .map(ObjectError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            return ResponseEntity.badRequest().body(errors);
        }
        AppUser appUser = new AppUser();
        appUser.setUsername(userDto.getUsername());
        appUser.setPassword(userDto.getPassword());
        appUser.setFullName(userDto.getFullName());
        authService.registerAsAdmin(appUser);
        return ResponseEntity.ok("User saved successfully");
    }

}
