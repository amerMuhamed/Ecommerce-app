package com.spring.eCommerce.Controller;

import com.spring.eCommerce.dto.JWTResponse;
import com.spring.eCommerce.dto.LoginRequest;
import com.spring.eCommerce.dto.LogoutRequest;
import com.spring.eCommerce.dto.UserRegistrationDto;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.service.AuthService;
import com.spring.eCommerce.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    @Autowired
    private final AuthService authService;
    @Autowired
    private final UserService userService;
    @PostMapping("/login")
    public ResponseEntity<JWTResponse> login(@RequestBody LoginRequest loginRequest) {
        JWTResponse jwtResponse = authService.login(loginRequest.username(), loginRequest.password());
        return ResponseEntity.ok(jwtResponse); }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok("Logged out successfully");
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestParam String refreshToken) {
        JWTResponse jwtResponse = authService.refreshAccessToken(refreshToken);
        if (jwtResponse == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired refresh token");
        }
        return ResponseEntity.ok(jwtResponse);
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
