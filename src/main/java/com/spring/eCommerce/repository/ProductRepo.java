package com.spring.eCommerce.repository;

import com.spring.eCommerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepo extends JpaRepository<Product, Long> {

    /** Includes soft-deleted products (used by the seeder so deleted seed products are not re-created). */
    Product findByName(String name);

    Product findByNameAndDeletedFalse(String name);

    List<Product> findAllByDeletedFalse();

    Optional<Product> findByIdAndDeletedFalse(Long id);
}
