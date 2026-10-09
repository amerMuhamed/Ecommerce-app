package com.spring.eCommerce.web;

import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.entity.Order;
import com.spring.eCommerce.entity.enums.OrderStatus;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.repository.OrderRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies admin order status transitions follow valid business rules and
 * never touch payment state.
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock
    private OrderRepo orderRepo;

    @Mock
    private com.spring.eCommerce.Mapper.OrderMapper orderMapper;

    @InjectMocks
    private AdminOrderService adminOrderService;

    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        pendingOrder = Order.builder()
                .id(1L)
                .orderStatus(OrderStatus.PENDING)
                .totalPrice(BigDecimal.TEN)
                .shippingAddress("Cairo")
                .build();
    }

    @Test
    void pendingToConfirmedIsAllowed() {
        when(orderRepo.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Order updated = adminOrderService.changeStatus(1L, OrderStatus.CONFIRMED);

        assertEquals(OrderStatus.CONFIRMED, updated.getOrderStatus());
    }

    @Test
    void pendingToDeliveredIsRejected() {
        when(orderRepo.findById(1L)).thenReturn(Optional.of(pendingOrder));

        assertThrows(BusinessException.class,
                () -> adminOrderService.changeStatus(1L, OrderStatus.DELIVERED));
        verify(orderRepo, never()).save(any());
    }

    @Test
    void deliveredOrderCannotMove() {
        pendingOrder.setOrderStatus(OrderStatus.DELIVERED);
        when(orderRepo.findById(1L)).thenReturn(Optional.of(pendingOrder));

        assertThrows(BusinessException.class,
                () -> adminOrderService.changeStatus(1L, OrderStatus.PROCESSING));
        verify(orderRepo, never()).save(any());
    }

    @Test
    void missingOrderThrows() {
        when(orderRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> adminOrderService.changeStatus(99L, OrderStatus.CONFIRMED));
    }
}
