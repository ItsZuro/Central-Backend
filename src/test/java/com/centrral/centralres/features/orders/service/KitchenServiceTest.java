package com.centrral.centralres.features.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.centrral.centralres.core.exceptions.BadRequestException;
import com.centrral.centralres.features.orders.model.Order;
import com.centrral.centralres.features.orders.model.OrderStatus;
import com.centrral.centralres.features.orders.model.OrderType;
import com.centrral.centralres.features.orders.repository.OrderRepository;
import com.centrral.centralres.features.orders.repository.OrderStatusRepository;

class KitchenServiceTest {
    @Mock OrderRepository orderRepository;
    @Mock OrderStatusRepository statusRepository;
    @InjectMocks KitchenService service;

    private Order order;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        order = Order.builder()
                .id(7L)
                .date(LocalDateTime.of(2026, 11, 22, 12, 30))
                .type(OrderType.builder().code("DINE_IN").build())
                .status(OrderStatus.builder().code("CONFIRMED").build())
                .details(new LinkedHashSet<>())
                .payments(new LinkedHashSet<>())
                .build();
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
    }

    @Test
    void chefCanBeginConfirmedDineInOrder() {
        when(statusRepository.findByCode("IN_PROGRESS"))
                .thenReturn(Optional.of(OrderStatus.builder().code("IN_PROGRESS").build()));

        var response = service.advance(7L, "IN_PROGRESS");

        assertThat(response.statusCode()).isEqualTo("IN_PROGRESS");
        verify(orderRepository).save(order);
    }

    @Test
    void chefCanCompleteDineInPreparation() {
        order.setStatus(OrderStatus.builder().code("IN_PROGRESS").build());
        when(statusRepository.findByCode("COMPLETED"))
                .thenReturn(Optional.of(OrderStatus.builder().code("COMPLETED").build()));

        assertThat(service.advance(7L, "COMPLETED").statusCode()).isEqualTo("COMPLETED");
    }

    @Test
    void chefCannotSkipPreparationStep() {
        assertThatThrownBy(() -> service.advance(7L, "COMPLETED"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void chefCannotCancelOrderOrManageDelivery() {
        assertThatThrownBy(() -> service.advance(7L, "CANCELLED"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.advance(7L, "OUT_FOR_DELIVERY"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deliveryRequiresPaymentBeforeKitchenCanBegin() {
        order.setType(OrderType.builder().code("DELIVERY").build());

        assertThatThrownBy(() -> service.advance(7L, "IN_PROGRESS"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("pago confirmado");
    }

    @Test
    void takeawayEndsWithReadyForPickup() {
        order.setType(OrderType.builder().code("TAKE_AWAY").build());
        order.setStatus(OrderStatus.builder().code("IN_PROGRESS").build());
        when(statusRepository.findByCode("READY_FOR_PICKUP"))
                .thenReturn(Optional.of(OrderStatus.builder().code("READY_FOR_PICKUP").build()));

        assertThat(service.advance(7L, "READY_FOR_PICKUP").statusCode())
                .isEqualTo("READY_FOR_PICKUP");
    }

    @Test
    void chefCannotMarkTakeawayAsCompleted() {
        order.setType(OrderType.builder().code("TAKE_AWAY").build());
        order.setStatus(OrderStatus.builder().code("IN_PROGRESS").build());
        assertThatThrownBy(() -> service.advance(7L, "COMPLETED"))
                .isInstanceOf(BadRequestException.class);
    }
}
