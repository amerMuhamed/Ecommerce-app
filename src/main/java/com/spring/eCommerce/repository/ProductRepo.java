package com.spring.eCommerce.repository;

import com.spring.eCommerce.entity.OrderItem;
import com.spring.eCommerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepo extends JpaRepository<Product, Long> {

    /** Includes soft-deleted products (used by the seeder so deleted seed products are not re-created). */
    Product findByName(String name);

    Product findByNameAndDeletedFalse(String name);

    List<Product> findAllByDeletedFalse();

    Optional<Product> findByIdAndDeletedFalse(Long id);

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
    }

    boolean existsByIdAndDeletedFalse(Long id);

    /**
     * Atomically takes {@code quantity} units of stock: the check and the decrement are one UPDATE, so two
     * concurrent orders can never both take the last unit. Returns false when the product is missing,
     * deleted or does not have enough stock. Must run inside the caller's transaction so the decrement rolls
     * back with the order. Managed Product instances keep their old availableQuantity in memory; it is never
     * written back because Product uses dynamic updates and callers do not set it.
     */
    default boolean reserveStock(Long productId, int quantity) {
        requirePositive(quantity);
        return decrementAvailableQuantity(productId, quantity) == 1;
    }

    /**
     * Atomically returns {@code quantity} units of stock (e.g. when a pending order is cancelled).
     */
    default void releaseStock(Long productId, int quantity) {
        requirePositive(quantity);
        incrementAvailableQuantity(productId, quantity);
    }

    /**
     * Returns a cancelled order's units to stock, in product-id order (same lock order as reservations).
     * Callers must hold the order's row lock and only call this once per cancellation.
     */
    default void releaseStock(List<OrderItem> orderItems) {
        orderItems.stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .forEach(item -> releaseStock(item.getProduct().getId(), item.getQuantity()));
    }

    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.availableQuantity = p.availableQuantity - :quantity "
            + "where p.id = :id and p.deleted = false and p.availableQuantity >= :quantity")
    int decrementAvailableQuantity(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.availableQuantity = p.availableQuantity + :quantity where p.id = :id")
    int incrementAvailableQuantity(@Param("id") Long id, @Param("quantity") int quantity);
}
