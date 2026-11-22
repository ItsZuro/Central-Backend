package com.centrral.centralres.features.orders.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import com.centrral.centralres.core.security.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centrral.centralres.core.config.NiubizConfig;
import com.centrral.centralres.core.security.repository.PaymentProfileRepository;
import com.centrral.centralres.features.orders.dto.niubiz.NiubizDTO;
import com.centrral.centralres.features.orders.dto.payment.request.PaymentInOrderRequest;
import com.centrral.centralres.features.orders.dto.payment.request.PaymentStartRequest;
import com.centrral.centralres.features.orders.dto.payment.request.PaymentUpdateRequest;
import com.centrral.centralres.features.orders.dto.payment.response.PaymentResponse;
import com.centrral.centralres.features.orders.enums.PaymentStatus;
import com.centrral.centralres.features.orders.model.Order;
import com.centrral.centralres.features.orders.model.OrderStatus;
import com.centrral.centralres.features.orders.model.Payment;
import com.centrral.centralres.features.orders.model.PaymentMethod;
import com.centrral.centralres.features.orders.repository.OrderRepository;
import com.centrral.centralres.features.orders.repository.OrderStatusRepository;
import com.centrral.centralres.features.orders.repository.PaymentMethodRepository;
import com.centrral.centralres.features.orders.repository.PaymentRepository;
import com.centrral.centralres.features.orders.specifications.PaymentSpecification;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

        private final PaymentRepository paymentRepository;
        private final OrderRepository orderRepository;
        private final PaymentMethodRepository paymentMethodRepository;
        private final PaymentProfileRepository paymentProfileRepository;
        private final OrderStatusRepository orderStatusRepository;
        private final NiubizService niubizService;
        private final NiubizConfig niubizConfig;

        public List<PaymentResponse> getAll(
                        Long customerId, Long paymentMethodId, LocalDateTime dateFrom, LocalDateTime dateTo) {

                Specification<Payment> spec = PaymentSpecification.withFilters(
                                customerId, paymentMethodId, dateFrom, dateTo);

                return paymentRepository.findAll(spec).stream()
                                .map(this::mapToResponse)
                                .toList();
        }

        public PaymentResponse getById(Long id) {
                Payment payment = paymentRepository.findById(id)
                                .orElseThrow(() -> new EntityNotFoundException("Pago no encontrado con id: " + id));
                return mapToResponse(payment);
        }

        @Transactional(readOnly = true)
        public NiubizDTO.PaymentStartResponse startOnlinePayment(PaymentStartRequest request, User user) {

                log.info("Iniciando sesión de pago Niubiz para orderId: {}", request.getOrderId());

                Order order = orderRepository.findById(request.getOrderId())
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Orden no encontrada: " + request.getOrderId()));

                assertPaymentOwnerOrStaff(order, user);
                validateOrderForPayment(order);
                requireMatchingAmount(order, request.getAmount());
                rejectAlreadyPaid(order);

                // --- CORRECCIÓN CRÍTICA: FORMATO PURCHASE NUMBER ---
                // Niubiz exige: Numérico y Max 12 dígitos.
                // Si tu ID de orden es 150, esto enviará "150".
                // Si necesitas unicidad por intentos fallidos, puedes usar una lógica más
                // compleja
                // pero para empezar, usa el ID directo.
                String purchaseNumber = String.valueOf(order.getId());

                // OPCIONAL: Si necesitas reintentos, podrías usar:
                // (orderId % 100000) + "" + (System.currentTimeMillis() % 1000000);
                // pero intenta primero con el ID limpio.

                if (purchaseNumber.length() > 12) {
                        throw new RuntimeException("El ID de la orden excede los 12 dígitos permitidos por Niubiz");
                }
                // ----------------------------------------------------

                NiubizDTO.PaymentStartResponse niubizResponse = niubizService.createPaymentSession(
                                request.getAmount(),
                                purchaseNumber);

                return NiubizDTO.PaymentStartResponse.builder()
                                .sessionKey(niubizResponse.getSessionKey())
                                .expirationTime(niubizResponse.getExpirationTime())
                                .merchantId(niubizConfig.getMerchant().getId())
                                .purchaseNumber(purchaseNumber)
                                .amount(request.getAmount())
                                .build();
        }

        @Transactional
        public PaymentResponse confirmOnlinePayment(NiubizDTO.PaymentConfirmRequest request, User user) {

                log.info("Confirmando pago Niubiz");

                Long orderId = Long.parseLong(request.getPurchaseNumber().split("-")[0]);

                Order order = orderRepository.findByIdForUpdate(orderId)
                                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada ID: " + orderId));

                validateOrderForPayment(order);
                requireMatchingAmount(order, request.getAmount());
                rejectAlreadyPaid(order);

                boolean isAuthorized = niubizService.authorizePayment(
                                request.getTransactionToken(),
                                request.getAmount(),
                                request.getPurchaseNumber());

                if (!isAuthorized) {
                        throw new RuntimeException("El pago fue rechazado o no autorizado por Niubiz");
                }

                PaymentMethod method = paymentMethodRepository.findByCodeAndProvider("CARD", "NIUBIZ")
                                .orElseThrow(() -> new EntityNotFoundException("Método de pago NIUBIZ no configurado"));

                Payment payment = Payment.builder()
                                .order(order)
                                .paymentMethod(method)
                                .amount(request.getAmount())
                                .isOnline(true)
                                .transactionCode(request.getTransactionToken())
                                .status(PaymentStatus.CONFIRMED)
                                .date(LocalDateTime.now())
                                .build();

                Payment saved = paymentRepository.save(payment);

                OrderStatus nextStatus = orderStatusRepository.findByCode("PENDING_CONFIRM")
                                .orElseGet(() -> orderStatusRepository.findByCode("CONFIRMED")
                                                .orElseThrow(() -> new EntityNotFoundException(
                                                                "Estado de confirmación no encontrado")));

                order.setStatus(nextStatus);
                orderRepository.save(order);

                return mapToResponse(saved);
        }

        @Transactional
        public void createInOrder(Order order, PaymentInOrderRequest request) {
                PaymentMethod method = paymentMethodRepository.findById(request.getPaymentMethodId())
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Método de pago no encontrado con id: "
                                                                + request.getPaymentMethodId()));

                // Un pago contraentrega no se confirma al crear la orden.
                PaymentStatus defaultStatus = PaymentStatus.PENDING;

                Payment payment = Payment.builder()
                                .order(order)
                                .paymentMethod(method)
                                .amount(request.getAmount())
                                .date(LocalDateTime.now())
                                .isOnline(request.getIsOnline())
                                .transactionCode(
                                                request.getTransactionCode() != null
                                                                ? request.getTransactionCode()
                                                                : UUID.randomUUID().toString())
                                .status(defaultStatus)
                                .build();

                order.getPayments().add(payment);
        }

        @Transactional
        public PaymentResponse update(Long id, PaymentUpdateRequest request) {
                Payment payment = paymentRepository.findById(id)
                                .orElseThrow(() -> new EntityNotFoundException("Pago no encontrado con id: " + id));

                if (request.getTransactionCode() != null) {
                        payment.setTransactionCode(request.getTransactionCode());
                }
                if (request.getStatus() != null) {
                        payment.setStatus(request.getStatus());
                }

                return mapToResponse(paymentRepository.save(payment));
        }

        @Transactional
        public void delete(Long id) {
                Payment payment = paymentRepository.findById(id)
                                .orElseThrow(() -> new EntityNotFoundException("Pago no encontrado con id: " + id));

                payment.setStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
        }

        private void assertPaymentOwnerOrStaff(Order order, User user) {
                if (user == null) throw new AccessDeniedException("Inicia sesión para pagar.");
                boolean staff = user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(role -> "ROLE_ADMIN".equals(role.getName())
                                || "ROLE_MANAGER".equals(role.getName())
                                || "ROLE_CASHIER".equals(role.getName()));
                boolean owner = order.getCustomer() != null
                        && order.getCustomer().getUser() != null
                        && user.getId().equals(order.getCustomer().getUser().getId());
                if (!owner && !staff) {
                        throw new AccessDeniedException("El pedido no pertenece a tu cuenta.");
                }
        }

        private void requireMatchingAmount(Order order, BigDecimal requestedAmount) {
                if (requestedAmount == null || order.getTotal() == null
                                || requestedAmount.compareTo(order.getTotal()) != 0) {
                        throw new IllegalArgumentException("El importe no coincide con el total calculado por el servidor.");
                }
        }

        private void rejectAlreadyPaid(Order order) {
                if (paymentRepository.existsByOrder_IdAndStatus(order.getId(), PaymentStatus.CONFIRMED)) {
                        throw new IllegalStateException("La orden ya tiene un pago confirmado.");
                }
        }

        private void validateOrderForPayment(Order order) {
                if (order.getType() == null) {
                        throw new IllegalArgumentException("El tipo de orden es nulo.");
                }
                String orderTypeCode = order.getType().getCode().toUpperCase();
                if (!"DELIVERY".equals(orderTypeCode) && !"TAKE_AWAY".equals(orderTypeCode)) {
                        throw new IllegalArgumentException("Pagos online no permitidos para: " + orderTypeCode);
                }
                if (order.getStatus() == null || Set.of("CANCELLED", "FAILED", "COMPLETED", "DELIVERED")
                        .contains(order.getStatus().getCode().toUpperCase())) {
                        throw new IllegalStateException("Este pedido ya no admite pagos online.");
                }
                if (order.getTotal() == null || order.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("El monto a pagar debe ser mayor a cero.");
                }
        }

        private PaymentResponse mapToResponse(Payment payment) {
                return PaymentResponse.builder()
                                .id(payment.getId())
                                .orderId(payment.getOrder().getId())
                                .paymentMethodId(payment.getPaymentMethod().getId())
                                .paymentMethodName(payment.getPaymentMethod().getCode())
                                .customerName(payment.getOrder().getCustomer().getUser().getFullName())
                                .amount(payment.getAmount())
                                .date(payment.getDate())
                                .isOnline(payment.getIsOnline())
                                .transactionCode(payment.getTransactionCode())
                                .provider(payment.getPaymentMethod().getProvider())
                                .status(payment.getStatus())
                                .build();
        }

}
