package com.spring.eCommerce.Controller;

import com.spring.eCommerce.dto.UploadImageResponse;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.service.user.UserService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<?> findAllUsers(){
        return ResponseEntity.ok(userService.findAll());
    }
    @GetMapping("/{id}")
    public ResponseEntity<?>findUserById(@PathVariable Long id){
        return ResponseEntity.ok(userService.findById(id));
    }
    @PostMapping("/saveUser")
    public ResponseEntity<String>CreateUser(@RequestBody AppUser user) {
        userService.save(user);
        return ResponseEntity.ok("User saved successfully");
    }
    @PostMapping("/Upload-profile-image")
    public ResponseEntity<?> uploadProfileImage(
            @RequestParam("image") MultipartFile image,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        AppUser user = userService.findByUserName(userDetails.getUsername());
        if (user == null) {
            return ResponseEntity.badRequest().body("User not found");
        }
        AppUser updatedUser = userService.uploadProfileImage(user, image);
        return ResponseEntity.ok(new UploadImageResponse( "Image uploaded successfully",updatedUser.getProfileImage().getProfileImageUrl()));
    }
    @Transactional
    @DeleteMapping("/delete-profile-image")
    public ResponseEntity<String> deleteProfileImage(@AuthenticationPrincipal UserDetails userDetails) {
        AppUser user = userService.findByUserName(userDetails.getUsername());
        if (user == null) {
            return ResponseEntity.badRequest().body("User not found");
        }
        String result = userService.deleteProfileImage(user);
        return ResponseEntity.ok(result);
    }


}
