package com.spring.eCommerce.service.image;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ImageService {
    private final Cloudinary cloudinary;

    public Map<String, String> uploadImage(MultipartFile image) throws IOException {
    Map uploadResult = cloudinary.uploader().upload(image.getBytes(), ObjectUtils.emptyMap());
    String imageUrl = uploadResult.get("secure_url").toString();
    String publicId = uploadResult.get("public_id").toString();
    return Map.of("imageUrl", imageUrl, "publicId", publicId);

    }
    public void deleteImage(String publicId) throws IOException {
        cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
    }

}
