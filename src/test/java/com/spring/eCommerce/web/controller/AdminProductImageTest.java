package com.spring.eCommerce.web.controller;

import com.spring.eCommerce.entity.Image;
import com.spring.eCommerce.entity.Product;
import com.spring.eCommerce.repository.OrderRepo;
import com.spring.eCommerce.repository.PaymentRepo;
import com.spring.eCommerce.repository.ProductRepo;
import com.spring.eCommerce.service.category.CategoryService;
import com.spring.eCommerce.service.image.ImageService;
import com.spring.eCommerce.service.product.ProductService;
import com.spring.eCommerce.web.AdminOrderService;
import com.spring.eCommerce.web.StorefrontService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** Updating a product's photo must replace the main image the shop displays, not append a hidden one. */
class AdminProductImageTest {

    private ProductRepo productRepo;
    private ImageService imageService;
    private MockMvc mockMvc;
    private Product product;

    @BeforeEach
    void setUp() {
        productRepo = mock(ProductRepo.class);
        imageService = mock(ImageService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(mock(ProductService.class),
                mock(CategoryService.class), productRepo, mock(OrderRepo.class), mock(PaymentRepo.class),
                imageService, mock(AdminOrderService.class), mock(StorefrontService.class))).build();

        product = Product.builder().id(5L).name("Baby Blanket").price(new BigDecimal("250.00"))
                .images(new ArrayList<>(List.of(
                        Image.builder().id(1L).imageUrl("https://img.example/old.jpg").publicId("old-id").build(),
                        Image.builder().id(2L).imageUrl("https://img.example/extra.jpg").build())))
                .build();
        when(productRepo.findById(5L)).thenReturn(Optional.of(product));
    }

    @Test
    void uploadedPhotoReplacesMainImage() throws Exception {
        when(imageService.uploadImage(any())).thenReturn(Map.of("imageUrl", "https://img.example/new.jpg", "publicId", "new-id"));

        mockMvc.perform(update("https://img.example/old.jpg")
                        .file(new MockMultipartFile("imageFile", "new.jpg", "image/jpeg", new byte[]{1, 2, 3})))
                .andExpect(redirectedUrl("/admin/products"));

        assertEquals(2, product.getImages().size());
        assertEquals("https://img.example/new.jpg", product.getImages().get(0).getImageUrl());
        assertEquals("new-id", product.getImages().get(0).getPublicId());
        verify(productRepo).save(product);
        verify(imageService).deleteImage("old-id");
    }

    @Test
    void changedImageUrlReplacesMainImage() throws Exception {
        mockMvc.perform(update("https://img.example/changed.jpg"))
                .andExpect(redirectedUrl("/admin/products"));

        assertEquals("https://img.example/changed.jpg", product.getImages().get(0).getImageUrl());
        assertNull(product.getImages().get(0).getPublicId());
        verify(imageService).deleteImage("old-id");
    }

    @Test
    void unchangedImageUrlLeavesImagesAlone() throws Exception {
        mockMvc.perform(update("https://img.example/old.jpg"))
                .andExpect(redirectedUrl("/admin/products"));

        assertEquals("https://img.example/old.jpg", product.getImages().get(0).getImageUrl());
        verify(productRepo, never()).save(any());
        verify(imageService, never()).deleteImage(any());
    }

    @Test
    void failedUploadKeepsOldPhotoAndTellsTheAdmin() throws Exception {
        when(imageService.uploadImage(any())).thenThrow(new IOException("cloudinary down"));

        mockMvc.perform(update("https://img.example/old.jpg")
                        .file(new MockMultipartFile("imageFile", "new.jpg", "image/jpeg", new byte[]{1})))
                .andExpect(redirectedUrl("/admin/products"))
                .andExpect(flash().attributeExists("adminError"));

        assertEquals("https://img.example/old.jpg", product.getImages().get(0).getImageUrl());
        verify(productRepo, never()).save(any());
    }

    private MockMultipartHttpServletRequestBuilder update(String imageUrl) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/admin/products/5/edit")
                .param("name", "Baby Blanket").param("description", "Soft")
                .param("price", "250.00").param("availableQuantity", "3")
                .param("imageUrl", imageUrl);
    }
}
