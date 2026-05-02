package com.spring.eCommerce.Controller;

import com.spring.eCommerce.dto.UpdateUserRequest;
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

    @PutMapping("/update")
    public ResponseEntity<?> updateCurrentUser(@RequestBody UpdateUserRequest updateUserRequest, @AuthenticationPrincipal UserDetails userDetails    ) {
        AppUser currentUser = userService.findByUserName(userDetails.getUsername());
        if (currentUser == null) {
            return ResponseEntity.badRequest().body("User not found");
        }

        try {
            AppUser savedUser = userService.updateUser(currentUser.getId(), updateUserRequest);
            return ResponseEntity.ok(savedUser);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/Upload-profile-image")
    public ResponseEntity<?> uploadProfileImage(@RequestParam("image") MultipartFile image,@AuthenticationPrincipal UserDetails userDetails) {
        AppUser user = userService.findByUserName(userDetails.getUsername());
        if (user == null) {
            return ResponseEntity.badRequest().body("User not found");
        }
        AppUser updatedUser = userService.uploadProfileImage(user, image);
        return ResponseEntity.ok(new UploadImageResponse("Image uploaded successfully", updatedUser.getImage().getImageUrl()));
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
