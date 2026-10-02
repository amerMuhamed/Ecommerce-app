package com.spring.eCommerce.service.order;

import com.spring.eCommerce.dto.order.OrderResponseDto;

public interface OrderService {
    OrderResponseDto createOrder(String shippingAddress);
}
