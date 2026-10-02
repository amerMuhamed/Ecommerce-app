package com.spring.eCommerce.dto.order;

import com.spring.eCommerce.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public record OrderResponseDto(
        Long id,
        Long userId,
        OrderStatus orderStatus,
        BigDecimal totalPrice,
        String shippingAddress,
        List<OrderItemResponse> items,
        Date createdDate,
        Date modifiedDate
) {
}
