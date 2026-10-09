package com.spring.eCommerce.service.order;

import com.spring.eCommerce.Mapper.OrderMapper;
import com.spring.eCommerce.dto.order.OrderResponseDto;
import com.spring.eCommerce.entity.*;
import com.spring.eCommerce.entity.enums.OrderStatus;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.repository.OrderRepo;
import com.spring.eCommerce.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepo orderRepo;
    private final UserService userService;
    private final OrderMapper orderMapper;

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

        for (CartItem cartItem : cart.getCartItems()) {

            int cartItemQty = cartItem.getQuantity();
            Product product = cartItem.getProduct();
            if (product.isDeleted()) {
                throw new IllegalStateException("Product is no longer available: " + product.getName());
            }
            int availableProductQty = product.getAvailableQuantity();

            if (cartItemQty <= 0) {
                throw new IllegalStateException("Cart item quantity must be greater than zero.");
            }

            if (cartItemQty > availableProductQty) {
                throw new IllegalStateException(
                        "Not enough quantity available for product: " + product.getName()
                );
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItemQty);
            orderItem.setPrice(product.getPrice());

            product.setAvailableQuantity(
                    availableProductQty - cartItemQty
            );

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

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("Order is already cancelled.");
        }

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new BusinessException(
                    "Cannot cancel order with status: " + order.getOrderStatus()
            );
        }

        // Restore stock
        for (OrderItem orderItem : order.getOrderItems()) {
            Product product = orderItem.getProduct();
            product.setAvailableQuantity(
                    product.getAvailableQuantity() + orderItem.getQuantity()
            );
        }

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
