package com.spring.eCommerce.config;

import com.spring.eCommerce.entity.Category;
import com.spring.eCommerce.entity.Product;
import com.spring.eCommerce.repository.CategoryRepo;
import com.spring.eCommerce.repository.ProductRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies the demo seed is idempotent and never duplicates records.
 */
@ExtendWith(MockitoExtension.class)
class LulumiSeedRunnerTest {

    @Mock
    private CategoryRepo categoryRepo;

    @Mock
    private ProductRepo productRepo;

    @InjectMocks
    private LulumiSeedRunner seedRunner;

    @Test
    void secondRunCreatesNothingWhenEverythingExists() throws Exception {
        Category existing = Category.builder().id(1L).name("Baby Clothing").build();
        when(categoryRepo.findByName(anyString())).thenReturn(existing);
        when(productRepo.findByName(anyString())).thenReturn(
                Product.builder().id(1L).name("x").price(BigDecimal.ONE).build());

        seedRunner.run();

        verify(categoryRepo, never()).save(any());
        verify(productRepo, never()).save(any());
    }

    @Test
    void missingRecordsAreCreated() throws Exception {
        when(categoryRepo.findByName(anyString())).thenReturn(null);
        when(categoryRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(productRepo.findByName(anyString())).thenReturn(null);

        seedRunner.run();

        verify(categoryRepo, times(7)).save(any());
        verify(productRepo, times(20)).save(any());
    }

    @Test
    void existingProductsArePreserved() throws Exception {
        when(categoryRepo.findByName(anyString()))
                .thenReturn(Category.builder().id(1L).name("c").products(new ArrayList<>()).build());
        // Only the first product already exists; the rest must be created.
        when(productRepo.findByName("Organic Cotton Baby Bodysuit"))
                .thenReturn(Product.builder().id(9L).name("Organic Cotton Baby Bodysuit").build());
        when(productRepo.findByName(argThat(name -> !"Organic Cotton Baby Bodysuit".equals(name))))
                .thenReturn(null);

        seedRunner.run();

        verify(productRepo, times(19)).save(any());
        verify(productRepo, never()).deleteById(any());
    }
}
