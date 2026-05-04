package com.spring.eCommerce.service.user;

import com.spring.eCommerce.dto.user.UpdateUserRequest;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.entity.Image;
import com.spring.eCommerce.repository.ImageRepo;
import com.spring.eCommerce.repository.UserRepo;
import com.spring.eCommerce.service.image.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepo userRepo;
    private final ImageRepo imageRepo;
    private final PasswordEncoder passwordEncoder;
    private final ImageService imageUpload;

    public AppUser findById(Long id) {
        return userRepo.findById(id).orElse(null);
    }

    public List<AppUser> findAll() {
        return userRepo.findAll();
    }

    public AppUser findByUserName(String username) {
        return userRepo.findByUsername(username).orElse(null);
    }

    public AppUser save(AppUser user) {
        AppUser existingUser = findByUserName(user.getUsername());
        if (existingUser != null) {
            throw new IllegalStateException("User already exists");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepo.save(user);
    }
    public AppUser updateUser(Long id, UpdateUserRequest request) {
        AppUser existingUser = userRepo.findById(id).orElseThrow(() ->
                new IllegalStateException("User not found"));

        if (request.getFullName() != null)
            existingUser.setFullName(request.getFullName());

        if (request.getUsername() != null)
            existingUser.setUsername(request.getUsername());

        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        return userRepo.save(existingUser);
    }



    public void delete(AppUser user) {
        if (user == null || findByUserName(user.getUsername()) == null) {
            throw new IllegalStateException("User not found to delete it");
        }
        userRepo.delete(user);
    }

    public AppUser uploadProfileImage(AppUser user, MultipartFile image) {
        if (user == null) {
            throw new IllegalStateException("User not found to upload profile image");
        }
        try {
            Image oldImage = user.getImage();
            if (oldImage != null && oldImage.getPublicId() != null) {
                imageUpload.deleteImage(oldImage.getPublicId());
            }
            Map<String, String> result = imageUpload.uploadImage(image);
            Image profileImage = Image.builder()
                    .imageUrl(result.get("imageUrl"))
                    .publicId(result.get("publicId"))
                    .build();

            user.setImage(profileImage);
            return userRepo.save(user);

        } catch (IOException e) {
            throw new IllegalStateException("Failed to upload profile image");
        }
    }

    public String deleteProfileImage(AppUser user) {
        if (user == null) {
            throw new IllegalStateException("User not found to delete profile image");
        }
        Image oldImage = user.getImage();
        if (oldImage != null && oldImage.getPublicId() != null) {
            try {
                imageUpload.deleteImage(oldImage.getPublicId());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to delete profile image");
            }
            user.setImage(null);
            imageRepo.delete(oldImage);
            userRepo.save(user);
            return "Profile image deleted successfully";
        }
        return "No profile image to delete";
    }
}

