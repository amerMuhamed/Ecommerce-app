package com.spring.eCommerce.service.order;

import com.spring.eCommerce.entity.*;
import com.spring.eCommerce.entity.enums.OrderStatus;
import com.spring.eCommerce.repository.OrderRepo;
import com.spring.eCommerce.repository.ProductRepo;
import com.spring.eCommerce.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepo orderRepo;
    private final UserService userService;
    private final ProductRepo productRepo;

    @Transactional
    @Override
    public void createOrder(String shippingAddress) {

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
        orderRepo.save(order);

        cart.clearItems();
    }
}
