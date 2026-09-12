package com.spring.eCommerce.repository;

import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.entity.Cart;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface CartRepo extends CrudRepository<Cart, Long> {
    Optional<Cart> findByUser(AppUser AppUser);
}
