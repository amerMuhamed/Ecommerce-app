package com.spring.eCommerce.repository;

import com.spring.eCommerce.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepo extends JpaRepository<Category, Long> {

    Category findByName(String name);

    @Modifying
    @Query(value = "DELETE FROM product_category WHERE category_id = :categoryId", nativeQuery = true)
    void unlinkProducts(@Param("categoryId") Long categoryId);

}
