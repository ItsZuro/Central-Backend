package com.centrral.centralres.features.orders.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centrral.centralres.core.exceptions.BadRequestException;
import com.centrral.centralres.core.exceptions.ConflictException;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.core.security.repository.UserRepository;
import com.centrral.centralres.features.orders.dto.cash.CashRequests;
import com.centrral.centralres.features.orders.dto.cash.CashShiftResponse;
import com.centrral.centralres.features.orders.enums.PaymentStatus;
import com.centrral.centralres.features.orders.model.CashEntry;
import com.centrral.centralres.features.orders.model.CashShift;
import com.centrral.centralres.features.orders.model.Order;
import com.centrral.centralres.features.orders.model.OrderStatus;
import com.centrral.centralres.features.orders.model.Payment;
import com.centrral.centralres.features.orders.model.PaymentMethod;
import com.centrral.centralres.features.orders.repository.CashEntryRepository;
import com.centrral.centralres.features.orders.repository.CashShiftRepository;
import com.centrral.centralres.features.orders.repository.OrderRepository;
import com.centrral.centralres.features.orders.repository.OrderStatusRepository;
import com.centrral.centralres.features.orders.repository.PaymentMethodRepository;
import com.centrral.centralres.features.orders.repository.PaymentRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashRegisterService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final Set<String> LOCAL_METHODS = Set.of("CASH", "YAPE", "PLIN", "TRANSFER");

    private final CashShiftRepository shifts;
    private final CashEntryRepository entries;
    private final UserRepository users;
    private final OrderRepository orders;
    private final OrderStatusRepository statuses;
    private final PaymentMethodRepository methods;
    private final PaymentRepository payments;

    private Long requireCashier(User user) {
        if (user == null || !user.hasRole("ROLE_CASHIER") || user.getId() == null) {
            throw new AccessDeniedException("Solo una cuenta de cajero puede manejar su propia caja.");
        }
        return user.getId();
    }

    @Transactional
    public CashShiftResponse open(User operator, CashRequests.Open request) {
        Long id = requireCashier(operator);
        users.findByIdForCashShift(id).orElseThrow(() -> new AccessDeniedException("Cajero no encontrado."));
        if (shifts.findFirstByCashierUserIdAndClosedAtIsNull(id).isPresent()) {
            throw new ConflictException("Ya existe una caja abierta para este cajero.");
        }
        CashShift shift = new CashShift();
        shift.setCashierUserId(id);
        shift.setOpenedAt(Instant.now());
        shift.setOpeningCash(request.openingCash());
        shifts.save(shift);
        log.info("Caja abierta: shift={} cashier={}", shift.getId(), id);
        return response(shift);
    }

    @Transactional(readOnly = true)
    public CashShiftResponse current(User operator) {
        Long id = requireCashier(operator);
        return shifts.findFirstByCashierUserIdAndClosedAtIsNull(id)
                .map(this::response).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<CashShiftResponse> history(User operator) {
        Long id = requireCashier(operator);
        return shifts.findTop20ByCashierUserIdAndClosedAtIsNotNullOrderByClosedAtDesc(id)
                .stream().map(this::response).toList();
    }

    @Transactional
    public CashShiftResponse move(User operator, CashRequests.Movement request) {
        CashShift shift = lockedCurrent(requireCashier(operator));
        String type = request.type() == null ? "" : request.type().trim().toUpperCase();
        if (!Set.of("CASH_IN", "CASH_OUT").contains(type)) {
            throw new BadRequestException("Selecciona ingreso o retiro de efectivo.");
        }
        if ("CASH_OUT".equals(type)) {
            BigDecimal balance = cashExpected(shift);
            if (balance.compareTo(request.amount()) < 0) {
                throw new BadRequestException("El retiro supera el efectivo esperado en caja.");
            }
        }
        CashEntry movement = new CashEntry();
        movement.setShift(shift);
        movement.setType(type);
        movement.setMethodCode("CASH");
        movement.setAmount(request.amount());
        movement.setDescription(request.reason().trim());
        movement.setCreatedAt(Instant.now());
        entries.save(movement);
        return response(shift);
    }

    /**
     * Fully serialized cash collection: cashier shift lock -> order lock.
     * One confirmed payment per order; server calculates full amount.
     * Never impersonate online card authorization.
     */
    @Transactional
    public CashShiftResponse collect(User operator, Long orderId, CashRequests.Collect request) {
        CashShift shift = lockedCurrent(requireCashier(operator));
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido no encontrado."));
        String type = order.getType() == null ? "" : order.getType().getCode();
        if ("DELIVERY".equalsIgnoreCase(type)) {
            throw new BadRequestException("Los delivery solo se cobran mediante su pasarela online.");
        }
        if (!Set.of("DINE_IN", "TAKE_AWAY").contains(type.toUpperCase())) {
            throw new BadRequestException("Este pedido no admite cobros presenciales.");
        }
        String status = order.getStatus() == null ? "" : order.getStatus().getCode();
        if (Set.of("CANCELLED", "FAILED", "REFUNDED").contains(status)) {
            throw new BadRequestException("No se puede cobrar un pedido anulado o fallido.");
        }
        if (order.getTotal() == null || order.getTotal().compareTo(ZERO) <= 0) {
            throw new BadRequestException("El pedido no tiene un importe positivo confirmado.");
        }
        if (payments.existsByOrder_IdAndStatus(orderId, PaymentStatus.CONFIRMED)) {
            throw new ConflictException("El pedido ya tiene un pago confirmado. No se puede cobrar dos veces.");
        }

        PaymentMethod method = methods.findById(request.paymentMethodId())
                .orElseThrow(() -> new EntityNotFoundException("Método de pago inexistente."));
        String code = method.getCode().toUpperCase();
        if (!LOCAL_METHODS.contains(code) || !"INTERNAL".equalsIgnoreCase(method.getProvider())) {
            throw new BadRequestException("Método de caja no permitido. Las tarjetas se verifican mediante Niubiz.");
        }

        String ref = request.reference() == null ? null : request.reference().trim();
        if (!"CASH".equals(code)) {
            if (ref == null || ref.length() < 6) {
                throw new BadRequestException("Yape, Plin y transferencias requieren referencia de al menos 6 caracteres.");
            }
            if (payments.existsByPaymentMethod_IdAndTransactionCodeAndStatus(
                    method.getId(), ref, PaymentStatus.CONFIRMED)) {
                throw new ConflictException("Esta referencia ya está asociada con otro pago confirmado.");
            }
        } else {
            ref = null;
        }

        Payment existingTender = order.getPayments().stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING
                        && p.getPaymentMethod().getId().equals(method.getId()))
                .findFirst().orElse(null);
        boolean newTender = existingTender == null;
        Payment payment = newTender ? new Payment() : existingTender;
        payment.setOrder(order);
        payment.setPaymentMethod(method);
        payment.setAmount(order.getTotal());
        payment.setDate(LocalDateTime.now());
        payment.setIsOnline(false);
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setTransactionCode("CASH".equals(code) ? UUID.randomUUID().toString() : ref);
        payment = payments.save(payment);
        if (newTender) {
            order.getPayments().add(payment);
        }

        // Existing draft tender rows no longer represent money owed once paid.
        for (Payment pending : order.getPayments()) {
            if (pending.getStatus() == PaymentStatus.PENDING && pending.getId() != null
                    && !pending.getId().equals(payment.getId())) {
                pending.setStatus(PaymentStatus.CANCELLED);
                payments.save(pending);
            }
        }

        CashEntry entry = new CashEntry();
        entry.setShift(shift);
        entry.setPayment(payment);
        entry.setOrderId(orderId);
        entry.setType("SALE");
        entry.setMethodCode(code);
        entry.setReference(ref);
        entry.setAmount(order.getTotal());
        entry.setDescription("Cobro presencial · pedido #" + orderId);
        entry.setCreatedAt(Instant.now());
        entries.save(entry);

        if (Set.of("PENDING", "PENDING_CONFIRMATION").contains(status)) {
            OrderStatus confirmed = statuses.findByCode("CONFIRMED")
                    .orElseThrow(() -> new EntityNotFoundException("Estado confirmado no configurado."));
            order.setStatus(confirmed);
            orders.save(order);
        }
        log.info("Cobro de caja registrado: shift={} order={} method={} payment={}",
                shift.getId(), orderId, code, payment.getId());
        return response(shift);
    }

    @Transactional
    public CashShiftResponse close(User operator, CashRequests.Close request) {
        CashShift shift = lockedCurrent(requireCashier(operator));
        BigDecimal expected = cashExpected(shift);
        BigDecimal difference = request.countedCash().subtract(expected);
        String note = request.note() == null ? "" : request.note().trim();
        if (difference.compareTo(ZERO) != 0 && note.length() < 10) {
            throw new BadRequestException("Explica la diferencia de arqueo (mínimo 10 caracteres).");
        }

        // Never silently reconcile refunded/deleted payments with a closed shift.
        for (CashEntry entry : entries.findByShift_IdOrderByCreatedAtAscIdAsc(shift.getId())) {
            if ("SALE".equals(entry.getType()) && (entry.getPayment() == null
                    || entry.getPayment().getStatus() != PaymentStatus.CONFIRMED)) {
                throw new ConflictException("Hay un pago del turno cuyo estado cambió. Requiere revisión antes de cerrar.");
            }
        }
        shift.setExpectedCash(expected);
        shift.setCountedCash(request.countedCash());
        shift.setCashDifference(difference);
        shift.setClosingNote(note.isEmpty() ? null : note);
        shift.setClosedAt(Instant.now());
        shifts.save(shift);
        log.info("Caja cerrada: shift={} cashier={} difference={}", shift.getId(), shift.getCashierUserId(), difference);
        return response(shift);
    }

    private CashShift lockedCurrent(Long cashierId) {
        return shifts.lockCurrent(cashierId)
                .orElseThrow(() -> new ConflictException("Primero debes abrir tu turno de caja."));
    }

    private BigDecimal cashExpected(CashShift shift) {
        BigDecimal expected = shift.getOpeningCash();
        for (CashEntry entry : entries.findByShift_IdOrderByCreatedAtAscIdAsc(shift.getId())) {
            if ("CASH".equals(entry.getMethodCode())) {
                if ("SALE".equals(entry.getType()) || "CASH_IN".equals(entry.getType())) {
                    expected = expected.add(entry.getAmount());
                } else if ("CASH_OUT".equals(entry.getType())) {
                    expected = expected.subtract(entry.getAmount());
                }
            }
        }
        return expected;
    }

    private CashShiftResponse response(CashShift shift) {
        List<CashEntry> register = entries.findByShift_IdOrderByCreatedAtAscIdAsc(shift.getId());
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        BigDecimal cashIn = ZERO;
        BigDecimal cashOut = ZERO;
        int salesCount = 0;
        List<CashShiftResponse.Entry> lines = new ArrayList<>();
        for (CashEntry entry : register) {
            if ("SALE".equals(entry.getType())) {
                salesCount++;
                byMethod.merge(entry.getMethodCode(), entry.getAmount(), BigDecimal::add);
            } else if ("CASH_IN".equals(entry.getType())) cashIn = cashIn.add(entry.getAmount());
            else if ("CASH_OUT".equals(entry.getType())) cashOut = cashOut.add(entry.getAmount());
            lines.add(new CashShiftResponse.Entry(
                    entry.getId(), entry.getOrderId(), entry.getType(), entry.getMethodCode(),
                    entry.getAmount(), entry.getReference(), entry.getDescription(), entry.getCreatedAt()));
        }
        BigDecimal expected = shift.getClosedAt() != null ? shift.getExpectedCash()
                : shift.getOpeningCash().add(byMethod.getOrDefault("CASH", ZERO))
                    .add(cashIn).subtract(cashOut);
        return new CashShiftResponse(shift.getId(), shift.getCashierUserId(),
                shift.getOpenedAt(), shift.getClosedAt(), shift.getOpeningCash(), expected,
                shift.getCountedCash(), shift.getCashDifference(), shift.getClosingNote(),
                byMethod, cashIn, cashOut, salesCount, lines);
    }
}
