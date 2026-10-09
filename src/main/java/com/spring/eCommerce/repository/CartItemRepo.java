package com.spring.eCommerce.repository;

import com.spring.eCommerce.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepo extends JpaRepository<CartItem, Long> {

    @Modifying
    @Query("delete from CartItem c where c.product.id = :productId")
    int deleteByProductId(@Param("productId") Long productId);
}
