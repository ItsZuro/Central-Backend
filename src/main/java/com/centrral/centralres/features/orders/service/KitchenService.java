package com.centrral.centralres.features.orders.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centrral.centralres.core.exceptions.BadRequestException;
import com.centrral.centralres.core.config.PostgresWebSocketBridge;
import com.centrral.centralres.features.orders.dto.order.response.KitchenOrderResponse;
import com.centrral.centralres.features.orders.enums.PaymentStatus;
import com.centrral.centralres.features.orders.model.Order;
import com.centrral.centralres.features.orders.model.OrderStatus;
import com.centrral.centralres.features.orders.repository.OrderRepository;
import com.centrral.centralres.features.orders.repository.OrderStatusRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class KitchenService {
    private static final ZoneId RESTAURANT_ZONE = ZoneId.of("America/Lima");
    private static final List<String> ACTIVE = List.of(
            "CONFIRMED", "IN_PROGRESS", "READY_FOR_PICKUP");
    private static final List<String> RECENT = List.of("COMPLETED", "DELIVERED");

    private final OrderRepository orderRepository;
    private final OrderStatusRepository statusRepository;
    private final PostgresWebSocketBridge wsEvents;

    @Transactional(readOnly = true)
    public List<KitchenOrderResponse> board() {
        // Los pedidos legacy usan LocalDateTime sin zona: consultar en la misma
        // zona del JVM que los creó y mostrar la hora con offset explícito.
        LocalDateTime startOfToday = LocalDate.now(RESTAURANT_ZONE)
                .atStartOfDay(RESTAURANT_ZONE)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
        return orderRepository.findForKitchenBoard(ACTIVE, RECENT, startOfToday)
                .stream().map(this::toResponse).toList();
    }

    /** Only the transitions explicitly owned by kitchen can be performed here. */
    @Transactional
    public KitchenOrderResponse advance(Long id, String targetStatus) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Pedido no encontrado."));
        String current = order.getStatus().getCode();
        String target = targetStatus == null ? "" : targetStatus.trim().toUpperCase(Locale.ROOT);
        String type = order.getType().getCode().toUpperCase(Locale.ROOT);

        if (!Set.of("DINE_IN", "TAKE_AWAY", "DELIVERY").contains(type)) {
            throw new BadRequestException("Tipo de pedido no permitido para cocina.");
        }
        boolean start = "CONFIRMED".equals(current) && "IN_PROGRESS".equals(target);
        boolean ready = "IN_PROGRESS".equals(current)
                && (("DINE_IN".equals(type) && "COMPLETED".equals(target))
                    || (!"DINE_IN".equals(type) && "READY_FOR_PICKUP".equals(target)));
        if (!start && !ready) {
            throw new BadRequestException("La transición no corresponde al flujo de preparación.");
        }
        if ("DELIVERY".equals(type) && !paymentConfirmed(order)) {
            throw new BadRequestException("El delivery necesita el pago confirmado antes de entrar a cocina.");
        }

        OrderStatus next = statusRepository.findByCode(target)
                .orElseThrow(() -> new EntityNotFoundException("Estado de cocina no configurado."));
        order.setStatus(next);
        KitchenOrderResponse result = toResponse(orderRepository.save(order));
        wsEvents.publish("/topic/orders", Map.of("event", "ORDERS_CHANGED"));
        log.info("Cocina: pedido {} cambió de {} a {}", id, current, target);
        return result;
    }

    private boolean paymentConfirmed(Order order) {
        return order.getPayments() != null && order.getPayments().stream()
                .anyMatch(payment -> payment.getStatus() == PaymentStatus.CONFIRMED);
    }

    private KitchenOrderResponse toResponse(Order order) {
        String type = order.getType().getCode();
        return new KitchenOrderResponse(
                order.getId(), order.getDate().atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                order.getStatus().getCode(), type,
                order.getTable() != null ? order.getTable().getCode() : null,
                order.getPickupStore() != null ? order.getPickupStore().getName() : null,
                paymentConfirmed(order),
                order.getDetails().stream()
                        .map(item -> new KitchenOrderResponse.KitchenLine(
                                item.getId(), item.getProduct().getName(), item.getQuantity(),
                                item.getNotes(), item.getSelectedExtras()))
                        .toList());
    }
}
