package com.spring.eCommerce.service.product;

import com.spring.eCommerce.Mapper.ProductMapper;
import com.spring.eCommerce.entity.Category;
import com.spring.eCommerce.entity.Product;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.repository.CartItemRepo;
import com.spring.eCommerce.repository.CategoryRepo;
import com.spring.eCommerce.repository.ProductRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceImplTest {

    private ProductRepo productRepo;
    private CartItemRepo cartItemRepo;
    private ProductServiceImpl service;
    private Product product;

    @BeforeEach
    void setUp() {
        productRepo = mock(ProductRepo.class);
        cartItemRepo = mock(CartItemRepo.class);
        service = new ProductServiceImpl(productRepo, mock(ProductMapper.class), mock(CategoryRepo.class), cartItemRepo);
        product = Product.builder().id(5L).name("Baby Blanket").price(new BigDecimal("250.00"))
                .categories(new ArrayList<>(List.of(Category.builder().id(1L).name("Bedding").build())))
                .build();
    }

    @Test
    void deleteByIdFlagsProductInsteadOfRemovingIt() {
        when(productRepo.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(product));

        service.deleteById(5L);

        assertTrue(product.isDeleted());
        assertTrue(product.getCategories().isEmpty());
        verify(cartItemRepo).deleteByProductId(5L);
        verify(productRepo).save(product);
        verify(productRepo, never()).deleteById(any());
        verify(productRepo, never()).delete(any());
    }

    @Test
    void deleteByNameFlagsProductInsteadOfRemovingIt() {
        when(productRepo.findByNameAndDeletedFalse("Baby Blanket")).thenReturn(product);

        service.deleteByName("Baby Blanket");

        assertTrue(product.isDeleted());
        verify(productRepo, never()).deleteById(any());
    }

    @Test
    void deletingMissingOrAlreadyDeletedProductFails() {
        when(productRepo.findByIdAndDeletedFalse(5L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.deleteById(5L));
        verify(cartItemRepo, never()).deleteByProductId(any());
    }

    @Test
    void deletedProductsAreHiddenFromReads() {
        when(productRepo.findAllByDeletedFalse()).thenReturn(List.of());
        when(productRepo.findByIdAndDeletedFalse(5L)).thenReturn(Optional.empty());

        assertTrue(service.getAll().isEmpty());
        assertThrows(BusinessException.class, () -> service.getById(5L));
        verify(productRepo, never()).findAll();
    }
}
