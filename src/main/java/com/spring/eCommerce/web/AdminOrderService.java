package com.spring.eCommerce.web;

import com.spring.eCommerce.Mapper.OrderMapper;
import com.spring.eCommerce.dto.order.OrderResponseDto;
import com.spring.eCommerce.entity.Order;
import com.spring.eCommerce.entity.OrderItem;
import com.spring.eCommerce.entity.Product;
import com.spring.eCommerce.entity.enums.OrderStatus;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.repository.OrderRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin-only order queries and status transitions.
 * Payment verification is never bypassed here: this service only moves the
 * order fulfilment status and never touches payment records.
 */
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.CONFIRMED, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PROCESSING, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED));
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERED, Set.of());
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, Set.of());
    }

    private final OrderRepo orderRepo;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public List<Order> findAll() {
        return orderRepo.findAll();
    }

    @Transactional(readOnly = true)
    public Order findById(Long id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Order with id { " + id + " } not found"));
    }

    @Transactional
    public Order changeStatus(Long id, OrderStatus next) {
        Order order = findById(id);
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(order.getOrderStatus(), Set.of());
        if (!allowed.contains(next)) {
            throw new BusinessException(
                    "Cannot change order status from " + order.getOrderStatus() + " to " + next);
        }
        if (next == OrderStatus.CANCELLED && order.getOrderStatus() == OrderStatus.PENDING) {
            for (OrderItem item : order.getOrderItems()) {
                Product product = item.getProduct();
                product.setAvailableQuantity(product.getAvailableQuantity() + item.getQuantity());
            }
        }
        order.setOrderStatus(next);
        return orderRepo.save(order);
    }

    public OrderResponseDto toDto(Order order) {
        return orderMapper.toDto(order);
    }
}
