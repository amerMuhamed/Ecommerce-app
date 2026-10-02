package com.spring.eCommerce.Mapper;

import com.spring.eCommerce.dto.order.OrderItemResponse;
import com.spring.eCommerce.dto.order.OrderResponseDto;
import com.spring.eCommerce.entity.Order;
import com.spring.eCommerce.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "userId", source = "appUser.id")
    @Mapping(target = "items", source = "orderItems")
    OrderResponseDto toDto(Order order);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "unitPrice", source = "price")
    @Mapping(target = "subTotal", expression = "java(item.getPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))")
    OrderItemResponse toDto(OrderItem item);
}
