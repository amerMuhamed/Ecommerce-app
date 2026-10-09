package com.spring.eCommerce.service.order;

import com.spring.eCommerce.Mapper.OrderMapper;
import com.spring.eCommerce.dto.order.OrderResponseDto;
import com.spring.eCommerce.entity.*;
import com.spring.eCommerce.entity.enums.OrderStatus;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.repository.OrderRepo;
import com.spring.eCommerce.repository.ProductRepo;
import com.spring.eCommerce.service.user.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepo orderRepo;
    private final ProductRepo productRepo;
    private final UserService userService;
    private final OrderMapper orderMapper;
    private final EntityManager entityManager;

    @Transactional
    @Override
    public OrderResponseDto createOrder(String shippingAddress) {

        AppUser appUser = userService.getCurrentUser();
        Cart cart = appUser.getCart();

        if (cart == null || cart.getCartItems().isEmpty()) {
            throw new IllegalStateException("Cart is empty. Cannot create order.");
        }

        Order order = new Order();
        BigDecimal totalPrice = BigDecimal.ZERO;

        // Reserve in product-id order so concurrent orders with overlapping products lock rows in the same
        // order (avoids deadlocks). Each reservation is an atomic conditional UPDATE; any failure throws and
        // rolls back every reservation made so far together with the order.
        List<CartItem> cartItems = cart.getCartItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();

        for (CartItem cartItem : cartItems) {

            int cartItemQty = cartItem.getQuantity();
            Product product = cartItem.getProduct();
            if (product.isDeleted()) {
                throw new IllegalStateException("Product is no longer available: " + product.getName());
            }

            if (cartItemQty <= 0) {
                throw new IllegalStateException("Cart item quantity must be greater than zero.");
            }

            if (!productRepo.reserveStock(product.getId(), cartItemQty)) {
                if (!productRepo.existsByIdAndDeletedFalse(product.getId())) {
                    throw new IllegalStateException("Product is no longer available: " + product.getName());
                }
                throw new IllegalStateException(
                        "Not enough quantity available for product: " + product.getName()
                );
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItemQty);
            orderItem.setPrice(product.getPrice());

            totalPrice = totalPrice.add(
                    product.getPrice().multiply(
                            BigDecimal.valueOf(cartItemQty)
                    )
            );

            order.addOrderItem(orderItem);
        }

        order.setShippingAddress(shippingAddress);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setTotalPrice(totalPrice);
        order.setAppUser(appUser);
        appUser.addOrder(order);
        Order savedOrder = orderRepo.save(order);

        cart.clearItems();

        return orderMapper.toDto(savedOrder);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponseDto> getMyOrders() {
        AppUser appUser = userService.getCurrentUser();
        return orderRepo.findByAppUserId(appUser.getId())
                .stream()
                .map(orderMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public OrderResponseDto getOrderById(Long id) {
        return orderMapper.toDto(getOwnedOrder(id));
    }

    @Transactional
    @Override
    public OrderResponseDto cancelOrder(Long id) {
        Order order = getOwnedOrder(id);
        // Re-read the order under a row lock: concurrent cancellations (customer or admin) and payment
        // confirmation are serialized, so stock is restored at most once.
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("Order is already cancelled.");
        }

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new BusinessException(
                    "Cannot cancel order with status: " + order.getOrderStatus()
            );
        }

        // Restore stock (atomic increments, product-id order).
        productRepo.releaseStock(order.getOrderItems());

        order.setOrderStatus(OrderStatus.CANCELLED);
        return orderMapper.toDto(orderRepo.save(order));
    }

    private Order getOwnedOrder(Long id) {
        AppUser appUser = userService.getCurrentUser();

        return orderRepo.findByIdAndAppUserId(id, appUser.getId())
                .orElseThrow(() ->
                        new BusinessException(
                                "Order with id { " + id + " } not found"
                        )
                );
    }
}
