package com.spring.eCommerce.service.order;

import com.spring.eCommerce.dto.order.OrderResponseDto;

import java.util.List;

public interface OrderService {
    OrderResponseDto createOrder(String shippingAddress);

    List<OrderResponseDto> getMyOrders();

    OrderResponseDto getOrderById(Long id);

    OrderResponseDto cancelOrder(Long id);
}
