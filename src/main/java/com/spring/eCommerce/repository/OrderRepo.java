package com.spring.eCommerce.repository;

import com.spring.eCommerce.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepo extends JpaRepository<Order, Long> {
    List<Order> findByAppUserId(Long userId);

    Optional<Order> findByIdAndAppUserId(Long id, Long userId);
}
