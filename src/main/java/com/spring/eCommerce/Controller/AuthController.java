package com.spring.eCommerce.Controller;

import com.spring.eCommerce.dto.JWTResponse;
import com.spring.eCommerce.dto.LoginRequest;
import com.spring.eCommerce.dto.LogoutRequest;
import com.spring.eCommerce.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    @Autowired
    private final AuthService authService;
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

}
