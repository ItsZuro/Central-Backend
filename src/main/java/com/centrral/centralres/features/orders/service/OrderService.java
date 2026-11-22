package com.centrral.centralres.features.orders.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centrral.centralres.core.config.TaxConfig;
import com.centrral.centralres.core.exceptions.ConflictException;
import com.centrral.centralres.features.orders.repository.CashShiftRepository;
import com.centrral.centralres.core.dto.response.PagedResponse;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.features.customers.model.Customer;
import com.centrral.centralres.features.customers.repository.CustomerRepository;
import com.centrral.centralres.features.employees.enums.EmploymentStatus;
import com.centrral.centralres.features.employees.model.Employee;
import com.centrral.centralres.features.employees.repository.EmployeeRepository;
import com.centrral.centralres.features.invoices.service.InvoiceGenerator;
import com.centrral.centralres.features.orders.dto.order.request.AssignDriverRequest;
import com.centrral.centralres.features.orders.dto.order.request.DeliveryAddressRequest;
import com.centrral.centralres.features.orders.dto.order.request.OrderRequest;
import com.centrral.centralres.features.orders.dto.order.request.UpdateLocationRequest;
import com.centrral.centralres.features.orders.dto.order.request.UpdateStatusRequest;
import com.centrral.centralres.features.orders.dto.order.response.DriverOptionResponse;
import com.centrral.centralres.features.orders.dto.order.response.OrderResponse;
import com.centrral.centralres.features.orders.dto.order.response.DeliveryQuoteResponse;
import com.centrral.centralres.features.orders.dto.order.response.OrderStatusStepResponse;
import com.centrral.centralres.features.orders.dto.orderdetail.request.OrderDetailInOrderRequest;
import com.centrral.centralres.features.orders.dto.orderdetail.response.OrderDetailInOrderResponse;
import com.centrral.centralres.features.orders.dto.payment.request.PaymentInOrderRequest;
import com.centrral.centralres.features.orders.model.Order;
import com.centrral.centralres.features.orders.model.OrderDetail;
import com.centrral.centralres.features.orders.model.Payment;
import com.centrral.centralres.features.orders.enums.PaymentStatus;
import com.centrral.centralres.features.orders.model.OrderStatus;
import com.centrral.centralres.features.orders.model.OrderStatusTranslation;
import com.centrral.centralres.features.orders.model.OrderType;
import com.centrral.centralres.features.orders.model.OrderTypeTranslation;
import com.centrral.centralres.features.orders.repository.OrderRepository;
import com.centrral.centralres.features.orders.repository.PaymentRepository;
import com.centrral.centralres.features.orders.repository.OrderStatusRepository;
import com.centrral.centralres.features.orders.repository.OrderTypeRepository;
import com.centrral.centralres.features.orders.repository.PaymentMethodRepository;
import com.centrral.centralres.features.products.enums.MovementSource;
import com.centrral.centralres.features.products.enums.MovementType;
import com.centrral.centralres.features.products.exceptions.InventoryNotFoundException;
import com.centrral.centralres.features.products.model.Ingredient;
import com.centrral.centralres.features.products.model.Inventory;
import com.centrral.centralres.features.products.model.InventoryMovement;
import com.centrral.centralres.features.products.model.Product;
import com.centrral.centralres.features.products.model.ProductIngredient;
import com.centrral.centralres.features.products.model.ProductExtra;
import com.centrral.centralres.features.products.repository.InventoryMovementRepository;
import com.centrral.centralres.features.products.repository.InventoryRepository;
import com.centrral.centralres.features.products.repository.ProductRepository;
import com.centrral.centralres.features.reports.dto.response.OrderTypeReportResponse;
import com.centrral.centralres.features.restaurant.enums.TableStatus;
import com.centrral.centralres.features.restaurant.model.Store;
import com.centrral.centralres.features.restaurant.model.TableEntity;
import com.centrral.centralres.features.restaurant.repository.StoreRepository;
import com.centrral.centralres.features.restaurant.repository.TableRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

        private final TaxConfig taxConfig;
        private final OrderRepository orderRepository;
        private final CustomerRepository customerRepository;
        private final EmployeeRepository employeeRepository;
        private final OrderStatusRepository statusRepository;
        private final OrderTypeRepository typeRepository;
        private final ProductRepository productRepository;
        private final InventoryRepository inventoryRepository;
        private final InventoryMovementRepository inventoryMovementRepository;
        private final DocumentService documentService;
        private final PaymentService paymentService;
        private final TableRepository tableRepository;
        private final StoreRepository storeRepository;
        private final DistanceMatrixService distanceMatrixService;
        private final DeliveryQuoteService deliveryQuoteService;
        private final InvoiceGenerator invoiceGenerator;
        private final PaymentMethodRepository paymentMethodRepository;
        private final CashShiftRepository cashShiftRepository;
        private final PaymentRepository paymentRepository;

        @Transactional(readOnly = true)
        public List<OrderResponse> getAll(String lang, String typeCode) {
                List<Order> orders;

                if (typeCode != null && !typeCode.isBlank()) {
                        log.info("Buscando órdenes filtradas por tipo: {}", typeCode);
                        orders = orderRepository.findByType_Code(typeCode);
                } else {
                        log.info("Buscando todas las órdenes");
                        orders = orderRepository.findAll();
                }

                return orders.stream()
                                .map(order -> mapToResponse(order, lang))
                                .toList();
        }

        public void verifyOrderVisibility(Long id, User user) {
                if (user == null) throw new AccessDeniedException("Usuario no autenticado.");
                Order order = orderRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada."));
                boolean isStaff = user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(r -> Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_CASHIER",
                                "ROLE_WAITER").contains(r.getName()));
                boolean isOwner = order.getCustomer() != null && order.getCustomer().getUser() != null
                        && user.getId().equals(order.getCustomer().getUser().getId());
                boolean isAssignedDriver = user.hasRole("ROLE_DRIVER")
                        && order.getEmployee() != null && order.getEmployee().getUser() != null
                        && user.getId().equals(order.getEmployee().getUser().getId());
                if (!isStaff && !isOwner && !isAssignedDriver) {
                        throw new AccessDeniedException("No tienes permiso para consultar este pedido.");
                }
        }

        @Transactional(readOnly = true)
        public OrderResponse getById(Long id, String lang) {
                Order order = orderRepository.findById(id)
                                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada con id: " + id));
                return mapToResponse(order, lang);
        }

        public OrderResponse getTrackingInfo(Long id, String lang) {
                Order order = orderRepository.findById(id)
                                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada con id: " + id));

                if (order.getType() != null &&
                                "DELIVERY".equalsIgnoreCase(order.getType().getCode()) &&
                                order.getDeliveryLatitude() != null && order.getDeliveryLongitude() != null) {

                        try {
                                Store mainStore = storeRepository.findAll().stream()
                                                .findFirst()
                                                .orElseThrow(() -> new IllegalStateException(
                                                                "No hay tiendas configuradas."));

                                double originLat = mainStore.getLatitude();
                                double originLng = mainStore.getLongitude();
                                double destLat = order.getDeliveryLatitude();
                                double destLng = order.getDeliveryLongitude();

                                // Consultar servicio DistanceMatrix (por ejemplo, Google API)
                                Map<String, Object> distanceResponse = distanceMatrixService
                                                .getDistanceAndDuration(originLat, originLng, destLat, destLng);

                                var rows = (List<?>) distanceResponse.get("rows");
                                if (rows != null && !rows.isEmpty()) {
                                        var elements = (List<?>) ((Map<?, ?>) rows.get(0)).get("elements");
                                        if (elements != null && !elements.isEmpty()) {
                                                Map<?, ?> element = (Map<?, ?>) elements.get(0);
                                                Map<?, ?> distance = (Map<?, ?>) element.get("distance");
                                                Map<?, ?> duration = (Map<?, ?>) element.get("duration");

                                                if (distance != null && duration != null) {
                                                        order.setEstimatedDistance(distance.get("text").toString());
                                                        order.setEstimatedDuration(duration.get("text").toString());
                                                }
                                        }
                                }
                        } catch (Exception e) {
                                log.error("Error recalculando distancia o duración estimada para tracking: {}",
                                                e.getMessage());
                        }
                }

                orderRepository.save(order);

                return mapToResponse(order, lang);
        }

        /**
         * Repartidores habilitados para asignación. No expone salarios,
         * documentos, correos ni usuarios que no sean de reparto.
         */
        @Transactional(readOnly = true)
        public List<DriverOptionResponse> getEligibleDrivers() {
                return employeeRepository.findAll().stream()
                        .filter(employee -> employee.getStatus() == EmploymentStatus.ACTIVE)
                        .filter(employee -> employee.getUser() != null
                                && employee.getUser().isEnabled()
                                && employee.getUser().hasRole("ROLE_DRIVER"))
                        .map(employee -> new DriverOptionResponse(
                                employee.getId(),
                                employee.getUser().getFullName(),
                                employee.getUser().getUsername()))
                        .sorted((a, b) -> a.fullName().compareToIgnoreCase(b.fullName()))
                        .toList();
        }

        @Transactional(readOnly = true)
        public List<OrderResponse> getAssignedDeliveries(User user, String lang) {
                Employee employee = employeeRepository.findByUserId(user.getId())
                        .orElseThrow(() -> new AccessDeniedException("No existe empleado asociado al usuario."));
                return orderRepository.findByEmployee_IdAndType_Code(employee.getId(), "DELIVERY")
                        .stream().map(order -> mapToResponse(order, lang)).toList();
        }

        @Transactional
        public OrderResponse updateAssignedDeliveryStatus(Long id, String newCode, User user, String lang) {
                Order order = orderRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new EntityNotFoundException("Pedido no encontrado."));
                if (order.getEmployee() == null || order.getType() == null
                        || !"DELIVERY".equalsIgnoreCase(order.getType().getCode())
                        || !order.getEmployee().getUser().getId().equals(user.getId())) {
                        throw new AccessDeniedException("El pedido no está asignado a este repartidor.");
                }
                String current = order.getStatus().getCode();
                ensureDeliveryPayment(order, order.getType().getCode(), newCode);
                boolean allowed = ("READY_FOR_PICKUP".equals(current) && "OUT_FOR_DELIVERY".equals(newCode))
                        || ("OUT_FOR_DELIVERY".equals(current) && "DELIVERED".equals(newCode));
                if (!allowed) throw new IllegalArgumentException("Transición de entrega no permitida.");
                order.setStatus(statusRepository.findByCode(newCode)
                        .orElseThrow(() -> new EntityNotFoundException("Estado de entrega inexistente.")));
                return mapToResponse(orderRepository.save(order), lang);
        }

        @Transactional(readOnly = true)
        public PagedResponse<OrderResponse> getCustomerOrdersForManagement(
                Long customerId, Pageable pageable, String lang) {
                if (!customerRepository.existsById(customerId)) {
                        throw new EntityNotFoundException("Cliente no encontrado.");
                }
                Page<Order> page = orderRepository.findByCustomer_Id(customerId, pageable);
                return PagedResponse.<OrderResponse>builder()
                        .content(page.getContent().stream().map(order -> mapToResponse(order, lang)).toList())
                        .pageNumber(page.getNumber())
                        .pageSize(page.getSize())
                        .totalElements(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .last(page.isLast())
                        .build();
        }

        @Transactional(readOnly = true)
        public List<OrderResponse> getOrdersForCurrentUser(User user, String lang) {
                Customer customer = customerRepository.findByUserId(user.getId())
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Cliente no encontrado para el usuario autenticado"));

                return orderRepository.findByCustomer(customer).stream()
                                .map(order -> mapToResponse(order, lang))
                                .toList();
        }

        /**
         * CREAR ORDEN (Cliente Web/Móvil)
         * Modificado para manejar lógica de pagos online vs offline.
         */
        @Transactional
        public OrderResponse create(User user, OrderRequest request, String lang) {
                Customer customer = customerRepository.findByUserId(user.getId())
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Cliente no encontrado para el usuario autenticado"));

                // 1. Construir base de la orden
                Order order = buildOrderBase(request, customer);
                // Los clientes no pueden decidir el estado ni asignarse empleados.
                order.setStatus(statusRepository.findByCode("PENDING")
                                .orElseThrow(() -> new EntityNotFoundException("Estado inicial PENDING inexistente.")));
                order.setEmployee(null);

                // El delivery online aún no se cobra al crear el pedido.
                // Bloquear pagos locales incluso si se modifica la petición HTTP.
                if (order.getType() != null && "DELIVERY".equalsIgnoreCase(order.getType().getCode())
                                && request.getPayments() != null && !request.getPayments().isEmpty()) {
                        throw new IllegalArgumentException("Para delivery debes pagar en línea mediante Niubiz.");
                }

                // 2. Calcular Totales y Detalles
                BigDecimal total = BigDecimal.ZERO;
                Set<OrderDetail> details = new LinkedHashSet<>();

                for (OrderDetailInOrderRequest d : request.getDetails()) {
                        Product product = productRepository.findById(d.getProductId())
                                        .orElseThrow(() -> new EntityNotFoundException(
                                                        "Producto no encontrado: " + d.getProductId()));

                        BigDecimal basePrice = unitPriceWithExtras(product, d);
                        BigDecimal taxRate = taxConfig.getRate();
                        BigDecimal priceWithTax = basePrice.multiply(BigDecimal.ONE.add(taxRate))
                                .setScale(2, RoundingMode.HALF_UP);

                        // Validar cantidad
                        if (d.getQuantity() == null || d.getQuantity() <= 0) {
                                throw new IllegalArgumentException(
                                                "Cantidad inválida para producto: " + product.getName());
                        }

                        BigDecimal lineTotal = priceWithTax.multiply(BigDecimal.valueOf(d.getQuantity()));

                        OrderDetail detail = OrderDetail.builder()
                                        .order(order)
                                        .product(product)
                                        .quantity(d.getQuantity())
                                        .unitPrice(priceWithTax)
                                        .selectedExtras(extraNames(product, d))
                                        .notes(d.getNotes())
                                        .build();

                        // Descontar inventario (Reserva temporal, si no paga se debería revertir por un
                        // job o al cancelar)
                        applyInventoryMovement(product, d.getQuantity(), false, null, "Reserva de orden pendiente");

                        details.add(detail);
                        total = total.add(lineTotal);
                }

                order.setDetails(details);
                order.setTotal(total);

                // 3. Calcular Tiempos de Entrega (Lógica Google Maps)
                calculateDeliveryEstimate(order, request);

                // 4. Guardar Orden Inicial
                Order savedOrder = orderRepository.save(order);

                // Actualizar referencia de inventario con el ID real de la orden
                savedOrder.getDetails().forEach(d -> applyInventoryMovement(d.getProduct(), 0, false,
                                savedOrder.getId(), "Asociar inventario a orden " + savedOrder.getId()));

                // 5. Manejo de Pagos
                // IMPORTANTE: Solo procesamos pagos aquí si NO SON ONLINE (Efectivo, POS, etc.)
                // Si es online (Niubiz), el pago se crea después en el
                // PaymentController/Service
                if (request.getPayments() != null) {
                        for (PaymentInOrderRequest p : request.getPayments()) {
                                // Verificar si es un método que requiere flujo externo
                                if (isOnlinePaymentMethod(p.getPaymentMethodId())) {
                                        log.info("Orden {} creada. Esperando pago online (Niubiz/MP).",
                                                        savedOrder.getId());
                                        // No creamos el payment entity aquí. El frontend iniciará el flujo con el ID de
                                        // la orden.
                                } else {
                                        // El servidor calcula el importe, no el navegador.
                                        p.setAmount(savedOrder.getTotal());
                                        paymentService.createInOrder(savedOrder, p);
                                }
                        }
                }

                // 6. Generar Documentos (Boleta/Factura) si aplica
                // Generalmente se generan AFTER payment, pero si es contraentrega se puede
                // pre-generar o esperar.
                if (request.getDocuments() != null && !isOnlineOrderPendingPayment(savedOrder, request)) {
                        request.getDocuments().forEach(d -> documentService.createInOrder(savedOrder, d));
                }

                return mapToResponse(orderRepository.save(savedOrder), lang);
        }

        @Transactional
        public OrderResponse createPosSale(User cashierUser, OrderRequest request, String lang) {
                if (cashierUser.hasRole("ROLE_CASHIER")
                        && cashShiftRepository.findFirstByCashierUserIdAndClosedAtIsNull(cashierUser.getId()).isEmpty()) {
                        throw new ConflictException("Abre tu turno de caja antes de registrar una venta.");
                }
                Employee cashierEmployee = employeeRepository.findByUserId(cashierUser.getId())
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Perfil de empleado no encontrado para el cajero autenticado"));

                // A walk-in customer has no account. Only associate a Customer when the
                // buyer explicitly identifies as a registered client.
                Customer customer = request.getCustomerId() == null ? null
                        : customerRepository.findById(request.getCustomerId())
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Cliente no encontrado con id: " + request.getCustomerId()));

                // A POS sale never takes payment instructions nor its status from the browser.
                // Payment is collected later through /cash-register, inside the cashier shift.
                if (request.getPayments() != null && !request.getPayments().isEmpty()) {
                        throw new com.centrral.centralres.core.exceptions.BadRequestException(
                                "Registra el cobro desde caja, no al crear la venta presencial.");
                }
                if (request.getDocuments() != null && !request.getDocuments().isEmpty()) {
                        throw new com.centrral.centralres.core.exceptions.BadRequestException(
                                "Los comprobantes se generan después de confirmar el pago.");
                }
                OrderType posType = typeRepository.findById(request.getTypeId())
                        .orElseThrow(() -> new EntityNotFoundException("Tipo de pedido inexistente."));
                if (!Set.of("DINE_IN", "TAKE_AWAY").contains(posType.getCode())) {
                        throw new com.centrral.centralres.core.exceptions.BadRequestException(
                                "La venta presencial solo admite salón o para llevar.");
                }

                // Neither the staff member nor the customer can mark a new sale as paid
                // or already confirmed by supplying another statusId.
                OrderStatus initial = statusRepository.findByCode("PENDING_CONFIRMATION")
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Estado inicial de venta presencial no configurado."));
                request.setEmployeeId(cashierEmployee.getId());
                request.setStatusId(initial.getId());
                Order order = buildOrderBase(request, customer);
                order.setStatus(initial);

                BigDecimal total = BigDecimal.ZERO;
                Set<OrderDetail> details = new LinkedHashSet<>();

                for (OrderDetailInOrderRequest d : request.getDetails()) {
                        Product product = productRepository.findById(d.getProductId())
                                        .orElseThrow(() -> new EntityNotFoundException(
                                                        "Producto no encontrado con id: " + d.getProductId()));

                        BigDecimal basePrice = unitPriceWithExtras(product, d);
                        if (basePrice == null) {
                                throw new IllegalStateException(
                                                "El producto con id " + product.getId() + " no tiene precio asignado.");
                        }

                        BigDecimal taxRate = taxConfig.getRate();
                        if (taxRate == null) {
                                throw new IllegalStateException("No está configurada la tasa de impuestos.");
                        }

                        BigDecimal priceWithTax = basePrice.multiply(BigDecimal.ONE.add(taxRate))
                                .setScale(2, RoundingMode.HALF_UP);
                        BigDecimal safeQuantity = d.getQuantity() != null ? BigDecimal.valueOf(d.getQuantity())
                                        : BigDecimal.ZERO;

                        if (safeQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                                throw new IllegalArgumentException(
                                                "Cantidad inválida para el producto con id " + d.getProductId());
                        }

                        BigDecimal lineTotal = priceWithTax.multiply(safeQuantity);

                        log.debug("Calculando línea -> productoId={}, priceWithTax={}, quantity={}, lineTotal={}",
                                        product.getId(), priceWithTax, safeQuantity, lineTotal);

                        OrderDetail detail = OrderDetail.builder()
                                        .order(order)
                                        .product(product)
                                        .quantity(d.getQuantity())
                                        .unitPrice(priceWithTax)
                                        .selectedExtras(extraNames(product, d))
                                        .notes(d.getNotes())
                                        .build();

                        applyInventoryMovement(product, d.getQuantity(), false, order.getId(), "Creación de orden");

                        details.add(detail);
                        total = total.add(lineTotal);
                }

                order.setDetails(details);
                order.setTotal(total);

                Order savedOrder = orderRepository.save(order);

                savePaymentsAndDocuments(savedOrder, request);

                Order finalSavedOrder = orderRepository.save(savedOrder);

                return mapToResponse(finalSavedOrder, lang);
        }

        @Transactional
        public OrderResponse update(Long id, OrderRequest request, String lang) {
                Order order = orderRepository.findByIdForUpdate(id)
                                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada con id: " + id));
                if (paymentRepository.existsByOrder_IdAndStatus(id, PaymentStatus.CONFIRMED)) {
                        throw new ConflictException("El pedido ya fue cobrado y no puede editarse sin una devolución auditada.");
                }

                if (request.getCustomerId() == null
                        && order.getType() != null && "DELIVERY".equals(order.getType().getCode())) {
                        throw new com.centrral.centralres.core.exceptions.BadRequestException(
                                "El delivery requiere un cliente identificado.");
                }
                order.setCustomer(request.getCustomerId() == null ? null
                        : customerRepository.findById(request.getCustomerId())
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Cliente no encontrado con id: " + request.getCustomerId())));
                order.setEmployee(request.getEmployeeId() != null ? employeeRepository.findById(request.getEmployeeId())
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Empleado no encontrado con id: " + request.getEmployeeId()))
                                : null);

                if (request.getDeliveryAddress() != null) {
                        DeliveryAddressRequest addrDto = request.getDeliveryAddress();
                        order.setDeliveryStreet(addrDto.getStreet());
                        order.setDeliveryReference(addrDto.getReference());
                        order.setDeliveryCity(addrDto.getCity());
                        order.setDeliveryInstructions(addrDto.getInstructions());
                        order.setDeliveryProvince(addrDto.getProvince());
                        order.setDeliveryZipCode(addrDto.getZipCode());
                        order.setDeliveryLatitude(addrDto.getLatitude());
                        order.setDeliveryLongitude(addrDto.getLongitude());
                }

                OrderStatus updatedStatus = statusRepository.findById(request.getStatusId())
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Estado de orden no encontrado con id: " + request.getStatusId()));
                OrderType updatedType = typeRepository.findById(request.getTypeId())
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Tipo de orden no encontrado con id: " + request.getTypeId()));
                if ("DELIVERY".equalsIgnoreCase(updatedType.getCode())
                                && request.getPayments() != null && !request.getPayments().isEmpty()) {
                        throw new IllegalStateException("Los pedidos delivery no aceptan pagos locales.");
                }
                ensureDeliveryPayment(order, updatedType.getCode(), updatedStatus.getCode());
                order.setStatus(updatedStatus);
                order.setType(updatedType);
                order.setDate(LocalDateTime.now());

                order.getDetails().forEach(detail -> applyInventoryMovement(detail.getProduct(), detail.getQuantity(),
                                true, order.getId(), "Actualización de orden"));

                Set<OrderDetail> updatedDetails = new LinkedHashSet<>();
                BigDecimal total = BigDecimal.ZERO;

                for (OrderDetailInOrderRequest d : request.getDetails()) {
                        Product product = productRepository.findById(d.getProductId())
                                        .orElseThrow(() -> new EntityNotFoundException(
                                                        "Producto no encontrado con id: " + d.getProductId()));
                        BigDecimal priceWithTax = unitPriceWithExtras(product, d)
                                        .multiply(BigDecimal.ONE.add(taxConfig.getRate()))
                                        .setScale(2, RoundingMode.HALF_UP);
                        OrderDetail detail = OrderDetail.builder()
                                        .order(order)
                                        .product(product)
                                        .quantity(d.getQuantity())
                                        .unitPrice(priceWithTax)
                                        .selectedExtras(extraNames(product, d))
                                        .notes(d.getNotes())
                                        .build();

                        applyInventoryMovement(product, d.getQuantity(), false, order.getId(),
                                        "Actualización de orden");

                        updatedDetails.add(detail);
                        total = total.add(priceWithTax.multiply(BigDecimal.valueOf(d.getQuantity())));
                }

                order.getDetails().clear();
                order.getDetails().addAll(updatedDetails);
                order.setTotal(total);

                savePaymentsAndDocuments(order, request);

                return mapToResponse(orderRepository.save(order), lang);
        }

        /**
         * Actualiza solo el estado de un pedido.
         * La seguridad ya fue validada en el Controlador.
         */
        @Transactional
        public OrderResponse updateStatus(Long id, UpdateStatusRequest request, String lang) {
                log.info("Actualizando estado para orden {} al estado: {}", id, request.getNewStatusCode());

                Order order = orderRepository.findByIdForUpdate(id)
                                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada con id: " + id));

                // ¡Ahora esto funciona gracias al paso 2!
                OrderStatus newStatus = statusRepository.findByCode(request.getNewStatusCode())
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Estado de orden no encontrado con código: "
                                                                + request.getNewStatusCode()));

                if (Set.of("CANCELLED", "FAILED", "REFUNDED").contains(newStatus.getCode())
                        && paymentRepository.existsByOrder_IdAndStatus(id, PaymentStatus.CONFIRMED)) {
                        throw new ConflictException("Un pedido cobrado no puede anularse sin un proceso de devolución auditado.");
                }
                if ("DELIVERY".equalsIgnoreCase(order.getType().getCode())
                        && Set.of("OUT_FOR_DELIVERY", "DELIVERED").contains(newStatus.getCode())) {
                        throw new ConflictException(
                                "El reparto debe iniciarse y confirmarse desde la cuenta del repartidor asignado.");
                }
                ensureDeliveryPayment(order, order.getType().getCode(), newStatus.getCode());
                order.setStatus(newStatus);
                Order savedOrder = orderRepository.save(order);

                log.info("Orden {} actualizada al estado: {}", savedOrder.getId(), newStatus.getCode());
                return mapToResponse(savedOrder, lang);
        }

        /**
         * Asigna un repartidor a un pedido.
         * La seguridad ya fue validada en el Controlador.
         */
        @Transactional
        public OrderResponse assignDriver(Long id, AssignDriverRequest request, String lang) {
                log.info("Asignando repartidor {} a la orden {}", request.getEmployeeId(), id);

                // Bloqueo transaccional: dos gerentes no pueden asignar simultáneamente
                // un mismo pedido ni reemplazar una ruta ya iniciada.
                Order order = orderRepository.findByIdForUpdate(id)
                                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada con id: " + id));

                if (order.getType() == null || !"DELIVERY".equalsIgnoreCase(order.getType().getCode())) {
                        throw new IllegalArgumentException("Solo se pueden asignar repartidores a pedidos delivery.");
                }
                String status = order.getStatus().getCode();
                if (Set.of("CANCELLED", "FAILED", "DELIVERED", "COMPLETED", "REFUNDED")
                                .contains(status)) {
                        throw new ConflictException("El pedido ya terminó y no admite asignación.");
                }
                // Recuperación controlada: versiones antiguas permitían marcar
                // OUT_FOR_DELIVERY sin empleado. En ese único caso se permite
                // vincularlo sin alterar el estado ni inventar una entrega.
                if ("OUT_FOR_DELIVERY".equals(status) && order.getEmployee() != null) {
                        throw new ConflictException(
                                "El reparto ya está en ruta con un responsable. No puede reasignarse.");
                }

                Employee driver = employeeRepository.findById(request.getEmployeeId())
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Empleado (repartidor) no encontrado con id: "
                                                                + request.getEmployeeId()));

                // Solo se puede asignar un reparto a un empleado con el rol DRIVER.
                if (driver.getUser() == null || !driver.getUser().hasRole("ROLE_DRIVER")) {
                        throw new IllegalArgumentException("El empleado seleccionado no tiene permisos de reparto.");
                }
                if (!driver.getUser().isEnabled() || driver.getStatus() != EmploymentStatus.ACTIVE) {
                        throw new ConflictException("Selecciona un repartidor activo con una cuenta habilitada.");
                }

                order.setEmployee(driver);
                Order savedOrder = orderRepository.save(order);

                log.info("Repartidor {} asignado a la orden {} (estado {})",
                                driver.getId(), savedOrder.getId(), status);
                return mapToResponse(savedOrder, lang);
        }

        /**
         * Actualiza la ubicación GPS del repartidor para una orden.
         * La seguridad fue validada en el controlador.
         * ¡PERO! Aún debemos validar que el empleado sea el CORRECTO.
         */
        @Transactional
        public void updateDeliveryLocation(Long id, UpdateLocationRequest request, User authenticatedUser) {

                Order order = orderRepository.findById(id)
                                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada con id: " + id));

                if (order.getEmployee() == null) {
                        log.warn("Intento de actualizar ubicación en orden {} sin repartidor asignado.", id);
                        throw new AccessDeniedException("Esta orden no tiene un repartidor asignado.");
                }

                if (!order.getEmployee().getUser().getId().equals(authenticatedUser.getId())) {
                        log.warn("Intento no autorizado de actualizar ubicación. Orden: {}, Repartidor Asignado: {}, Repartidor Autenticado: {}",
                                        id, order.getEmployee().getId(), authenticatedUser.getId());
                        throw new AccessDeniedException(
                                        "No estás autorizado para actualizar la ubicación de esta orden.");
                }

                // Si pasó la seguridad, actualizamos las coordenadas
                order.setCurrentLatitude(request.getLatitude());
                order.setCurrentLongitude(request.getLongitude());
                orderRepository.save(order);

                // (Opcional) Aquí es donde notificarías al cliente en tiempo real
                // via WebSockets.
                // notificationProducer.sendDeliveryLocationUpdate(id, request.getLatitude(),
                // request.getLongitude());

                log.debug("Ubicación actualizada para orden {}: ({}, {})", id, request.getLatitude(),
                                request.getLongitude());
        }

        @Transactional
        public void delete(Long id) {
                Order order = orderRepository.findById(id)
                                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada con id: " + id));
                if (paymentRepository.existsByOrder_IdAndStatus(id, PaymentStatus.CONFIRMED)) {
                        throw new ConflictException("No se puede eliminar un pedido con un pago confirmado.");
                }

                order.getDetails().forEach(detail -> applyInventoryMovement(detail.getProduct(), detail.getQuantity(),
                                true, order.getId(), "Eliminación de orden"));

                orderRepository.delete(order);
        }

        @Transactional
        public OrderResponse cancelOrder(Long id, User user, String lang) {
                Order order = orderRepository.findByIdForUpdate(id)
                                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada"));

                if (order.getStatus().getCode().equalsIgnoreCase("CANCELLED")) {
                        throw new IllegalStateException("La orden ya se encuentra cancelada.");
                }

                String currentStatus = order.getStatus().getCode();
                if (Set.of("COMPLETED", "DELIVERED", "OUT_FOR_DELIVERY").contains(currentStatus)) {
                        throw new IllegalStateException("No es posible cancelar un pedido ya despachado o finalizado.");
                }
                if (order.getPayments() != null && order.getPayments().stream()
                                .anyMatch(payment -> payment.getStatus()
                                                == com.centrral.centralres.features.orders.enums.PaymentStatus.CONFIRMED)) {
                        throw new IllegalStateException("La orden ya tiene un pago confirmado; requiere gestión de devolución.");
                }

                boolean isOwner = order.getCustomer() != null
                                && order.getCustomer().getUser() != null
                                && order.getCustomer().getUser().getId().equals(user.getId());

                boolean isAdmin = user.getRoles().stream()
                                .anyMatch(role -> role.getName().equalsIgnoreCase("ROLE_ADMIN"));

                if (!isOwner && !isAdmin) {
                        throw new SecurityException("El usuario no está autorizado para cancelar esta orden.");
                }

                if (!isAdmin && !CustomerOrderPolicy.canCustomerCancel(currentStatus, false)) {
                        throw new IllegalStateException("Solo se pueden cancelar pedidos pendientes desde la cuenta del cliente.");
                }

                OrderStatus cancelledStatus = statusRepository.findByCode("CANCELLED")
                                .orElseThrow(() -> new EntityNotFoundException("Estado 'CANCELLED' no encontrado"));
                order.setStatus(cancelledStatus);

                if (order.getDetails() != null && !order.getDetails().isEmpty()) {
                        for (OrderDetail detail : order.getDetails()) {
                                Product product = detail.getProduct();
                                if (product != null && product.getIngredients() != null) {
                                        for (ProductIngredient productIngredient : product.getIngredients()) {
                                                Ingredient ingredient = productIngredient.getIngredient();
                                                BigDecimal ingredientQty = BigDecimal
                                                                .valueOf(productIngredient.getQuantity())
                                                                .multiply(BigDecimal.valueOf(detail.getQuantity()));
                                                Inventory inventory = inventoryRepository.findByIngredient(ingredient)
                                                                .orElse(null);
                                                if (inventory != null) {
                                                        inventory.increaseStock(ingredientQty);
                                                        inventoryRepository.save(inventory);
                                                }
                                        }
                                }
                        }
                }

                orderRepository.save(order);

                // TODO: Enviar notificación ?
                // notificationProducer.sendOrderCancelledNotification(order);

                return mapToResponse(order, lang);
        }

        @Transactional
        public void addLocalPayment(Long orderId, PaymentInOrderRequest request) {
                Order order = orderRepository.findByIdForUpdate(orderId)
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Orden no encontrada con id: " + orderId));
                if (paymentRepository.existsByOrder_IdAndStatus(orderId, PaymentStatus.CONFIRMED)) {
                        throw new ConflictException("El pedido ya fue cobrado.");
                }
                if ("DELIVERY".equalsIgnoreCase(order.getType().getCode())) {
                        throw new IllegalStateException("No se aceptan pagos locales para delivery.");
                }
                if ("CANCELLED".equalsIgnoreCase(order.getStatus().getCode())) {
                        throw new IllegalStateException("No se puede cobrar un pedido cancelado.");
                }

                paymentService.createInOrder(order, request);
                orderRepository.save(order);
        }

        @Transactional(readOnly = true)
        public byte[] generateInvoiceForOrder(Long id) {

                Order order = orderRepository.findById(id)
                                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada con id: " + id));

                byte[] pdfBytes = invoiceGenerator.generateInvoice(order);

                if (pdfBytes == null || pdfBytes.length == 0) {
                        throw new RuntimeException("Error al generar PDF: el archivo está vacío.");
                }

                return pdfBytes;
        }

        private Order buildOrderBase(OrderRequest request, Customer customer) {
                Employee employee = request.getEmployeeId() != null ? employeeRepository
                                .findById(request.getEmployeeId())
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Empleado no encontrado con id: " + request.getEmployeeId()))
                                : null;

                OrderStatus status = statusRepository.findById(request.getStatusId())
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Estado de orden no encontrado con id: " + request.getStatusId()));
                OrderType type = typeRepository.findById(request.getTypeId())
                                .orElseThrow(() -> new EntityNotFoundException(
                                                "Tipo de orden no encontrado con id: " + request.getTypeId()));

                Order.OrderBuilder orderBuilder = Order.builder()
                                .customer(customer)
                                .employee(employee)
                                .status(status)
                                .type(type)
                                .date(LocalDateTime.now())
                                .total(BigDecimal.ZERO)
                                .details(new LinkedHashSet<>())
                                .payments(new LinkedHashSet<>())
                                .documents(new LinkedHashSet<>());

                String orderTypeCode = type.getCode() != null ? type.getCode().toUpperCase() : "";

                switch (orderTypeCode) {
                        case "DELIVERY":
                                if (request.getDeliveryAddress() == null) {
                                        throw new IllegalArgumentException(
                                                        "La dirección de entrega es obligatoria para órdenes a domicilio.");
                                }

                                DeliveryAddressRequest addrDto = request.getDeliveryAddress();
                                orderBuilder.deliveryStreet(addrDto.getStreet())
                                                .deliveryReference(addrDto.getReference())
                                                .deliveryCity(addrDto.getCity())
                                                .deliveryInstructions(addrDto.getInstructions())
                                                .deliveryProvince(addrDto.getProvince())
                                                .deliveryZipCode(addrDto.getZipCode())
                                                .deliveryLatitude(addrDto.getLatitude())
                                                .deliveryLongitude(addrDto.getLongitude());

                                break;

                        case "DINE_IN":
                                if (request.getTableId() == null) {
                                        throw new IllegalArgumentException(
                                                        "La mesa es obligatoria para órdenes presenciales.");
                                }
                                TableEntity selectedTable = tableRepository.findById(request.getTableId())
                                                .orElseThrow(() -> new EntityNotFoundException(
                                                                "Mesa no encontrada con id: " + request.getTableId()));

                                if (selectedTable.getStatus() != TableStatus.FREE) {
                                        throw new IllegalStateException(
                                                        "La mesa " + selectedTable.getCode() + " no está disponible.");
                                }

                                orderBuilder.table(selectedTable);

                                selectedTable.setStatus(TableStatus.OCCUPIED);
                                tableRepository.save(selectedTable);
                                log.info("Mesa {} marcada como OCUPADA por nueva orden.", selectedTable.getCode());

                                break;

                        case "TAKE_AWAY":
                                if (request.getPickupStoreId() == null) {
                                        throw new IllegalArgumentException(
                                                        "La tienda de recogida es obligatoria para órdenes para llevar.");
                                }

                                Store pickupStore = storeRepository.findById(request.getPickupStoreId())
                                                .orElseThrow(() -> new EntityNotFoundException(
                                                                "Tienda no encontrada con id: "
                                                                                + request.getPickupStoreId()));

                                orderBuilder.pickupStore(pickupStore);
                                break;

                        default:
                                log.warn("Tipo de orden no reconocido para lógica especial: {}", orderTypeCode);
                                break;
                }

                return orderBuilder.build();
        }

        private void savePaymentsAndDocuments(Order order, OrderRequest request) {
                order.getPayments().clear();
                if (request.getPayments() != null)
                        request.getPayments().forEach(p -> paymentService.createInOrder(order, p));

                order.getDocuments().clear();
                if (request.getDocuments() != null)
                        request.getDocuments().forEach(d -> documentService.createInOrder(order, d));
        }

        private void applyInventoryMovement(Product product, Integer quantity, boolean restore, Long referenceId,
                        String reason) {
                if (quantity == 0)
                        return;

                product.getIngredients().forEach(pi -> {
                        Inventory inventory = inventoryRepository.findByIngredient(pi.getIngredient())
                                        .orElseThrow(() -> new InventoryNotFoundException(
                                                        "Inventario no encontrado: " + pi.getIngredient().getName()));

                        BigDecimal totalQty = BigDecimal.valueOf(pi.getQuantity())
                                        .multiply(BigDecimal.valueOf(quantity));

                        if (restore)
                                inventory.increaseStock(totalQty);
                        else
                                inventory.decreaseStock(totalQty);

                        inventoryRepository.save(inventory);

                        InventoryMovement movement = InventoryMovement.builder()
                                        .ingredient(pi.getIngredient())
                                        .quantity(totalQty)
                                        .type(restore ? MovementType.ENTRY : MovementType.EXIT)
                                        .source(MovementSource.ORDER)
                                        .reason(reason)
                                        .referenceId(referenceId) 
                                        .date(LocalDateTime.now())
                                        .build();
                        inventoryMovementRepository.save(movement);

                        // Alertas de stock bajo
                        if (!restore && inventory.getCurrentStock().compareTo(inventory.getMinimumStock()) < 0
                                        && inventory.shouldNotifyLowStock()) {
                                inventory.markAlertSent();
                                inventoryRepository.save(inventory);
                        }
                });
        }

        private OrderResponse mapToResponse(Order order, String lang) {
                List<OrderStatusStepResponse> timeline = new ArrayList<>();
                if (order.getType() != null && order.getType().getStatusFlow() != null) {

                        timeline = order.getType().getStatusFlow().stream()
                                        .map(flowStep -> OrderStatusStepResponse.builder()
                                                        .code(flowStep.getOrderStatus().getCode())
                                                        .name(getStatusName(flowStep.getOrderStatus(), lang))
                                                        .step(flowStep.getStepOrder())
                                                        .build())
                                        .collect(Collectors.toList());
                }

                Payment displayPayment = order.getPayments() == null ? null : order.getPayments().stream()
                                .sorted((left, right) -> {
                                        int leftScore = left.getStatus() == PaymentStatus.CONFIRMED ? 1 : 0;
                                        int rightScore = right.getStatus() == PaymentStatus.CONFIRMED ? 1 : 0;
                                        return Integer.compare(rightScore, leftScore);
                                }).findFirst().orElse(null);
                boolean paid = order.getPayments() != null && order.getPayments().stream()
                                .anyMatch(payment -> payment.getStatus() == PaymentStatus.CONFIRMED);
                String paymentState = paid ? "CONFIRMED"
                                : displayPayment != null && displayPayment.getStatus() != null
                                        ? displayPayment.getStatus().name() : "PENDING";

                OrderResponse.OrderResponseBuilder responseBuilder = OrderResponse.builder()
                                .id(order.getId())
                                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                                .customerName(order.getCustomer() != null && order.getCustomer().getUser() != null
                                        ? order.getCustomer().getUser().getFullName() : "Consumidor final")
                                .employeeId(order.getEmployee() != null ? order.getEmployee().getId() : null)
                                .employeeName(order.getEmployee() != null ? order.getEmployee().getUser().getFullName()
                                                : null)
                                .date(order.getDate())
                                .statusId(order.getStatus().getId())
                                .statusCode(order.getStatus().getCode())
                                .statusName(getStatusName(order.getStatus(), lang))
                                .typeId(order.getType().getId())
                                .typeName(getTypeName(order.getType(), lang))
                                .total(order.getTotal())
                                .paymentStatus(paymentState)
                                .paymentMethodCode(displayPayment != null && displayPayment.getPaymentMethod() != null
                                                ? displayPayment.getPaymentMethod().getCode() : null)
                                .canCustomerCancel(CustomerOrderPolicy.canCustomerCancel(
                                                order.getStatus().getCode(), paid))
                                .details(order.getDetails().stream()
                                                .map(d -> OrderDetailInOrderResponse.builder()
                                                                .id(d.getId())
                                                                .productId(d.getProduct().getId())
                                                                .productName(d.getProduct().getName())
                                                                .productImageUrl(d.getProduct().getImageUrl())
                                                                .quantity(d.getQuantity())
                                                                .unitPrice(d.getUnitPrice())
                                                                .selectedExtras(d.getSelectedExtras())
                                                                .notes(d.getNotes())
                                                                .build())
                                                .toList())
                                .estimatedTime(order.getEstimatedTime())
                                .estimatedDistance(order.getEstimatedDistance())
                                .estimatedDuration(order.getEstimatedDuration())
                                .currentLatitude(order.getCurrentLatitude())
                                .currentLongitude(order.getCurrentLongitude())
                                .timelineSteps(timeline);
                ;

                String orderTypeCode = order.getType().getCode() != null ? order.getType().getCode().toUpperCase() : "";

                switch (orderTypeCode) {
                        case "DELIVERY":
                                responseBuilder
                                                .deliveryStreet(order.getDeliveryStreet())
                                                .deliveryReference(order.getDeliveryReference())
                                                .deliveryCity(order.getDeliveryCity())
                                                .deliveryInstructions(order.getDeliveryInstructions())
                                                .deliveryProvince(order.getDeliveryProvince())
                                                .deliveryZipCode(order.getDeliveryZipCode())
                                                .deliveryLatitude(order.getDeliveryLatitude())
                                                .deliveryLongitude(order.getDeliveryLongitude());
                                break;

                        case "DINE_IN":
                                if (order.getTable() != null) {
                                        responseBuilder.tableId(order.getTable().getId())
                                                        .tableCode(order.getTable().getCode());
                                }
                                break;
                        case "TAKE_AWAY":
                                if (order.getPickupStore() != null) {
                                        responseBuilder
                                                        .pickupStoreId(order.getPickupStore().getId())
                                                        .pickupStoreName(order.getPickupStore().getName())
                                                        .pickupStoreAddress(order.getPickupStore().getAddress());
                                }
                                break;
                }

                return responseBuilder.build();
        }

        private void ensureDeliveryPayment(Order order, String orderType, String targetStatus) {
                if (CustomerOrderPolicy.requiresConfirmedDeliveryPayment(orderType, targetStatus)
                                && (order.getPayments() == null || order.getPayments().stream()
                                        .noneMatch(payment -> payment.getStatus() == PaymentStatus.CONFIRMED))) {
                        throw new IllegalStateException(
                                "No se puede preparar o despachar un delivery sin pago confirmado.");
                }
        }

        private String normalizeLang(String lang) {
                if (lang == null || lang.isEmpty())
                        return "es";
                if (lang.startsWith("es"))
                        return "es";
                if (lang.startsWith("en"))
                        return "en";
                return "es";
        }

        private String getStatusName(OrderStatus status, String lang) {
                String normalizedLang = normalizeLang(lang);
                return status.getTranslations().stream()
                                .filter(t -> t.getLang().equalsIgnoreCase(normalizedLang))
                                .map(OrderStatusTranslation::getName)
                                .findFirst()
                                .orElse("Sin nombre");
        }

        private String getTypeName(OrderType type, String lang) {
                String normalizedLang = normalizeLang(lang);
                return type.getTranslations().stream()
                                .filter(t -> t.getLang().equalsIgnoreCase(normalizedLang))
                                .map(OrderTypeTranslation::getName)
                                .findFirst()
                                .orElse("Sin nombre");
        }

        private List<ProductExtra> selectedExtras(Product product, OrderDetailInOrderRequest request) {
                if (request.getExtraIds() == null || request.getExtraIds().isEmpty()) return List.of();
                Set<Long> unique = new LinkedHashSet<>(request.getExtraIds());
                if (unique.size() != request.getExtraIds().size()) {
                        throw new IllegalArgumentException("No se puede repetir un adicional en un mismo producto.");
                }
                return unique.stream().map(id -> product.getExtras().stream()
                        .filter(extra -> id != null && id.equals(extra.getId()) && extra.isActive())
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Adicional no disponible para el producto " + product.getId())))
                        .toList();
        }

        private BigDecimal unitPriceWithExtras(Product product, OrderDetailInOrderRequest request) {
                if (product.getPrice() == null) {
                        throw new IllegalStateException("Producto sin precio: " + product.getId());
                }
                return selectedExtras(product, request).stream()
                        .map(ProductExtra::getPrice)
                        .reduce(product.getPrice(), BigDecimal::add);
        }

        private String extraNames(Product product, OrderDetailInOrderRequest request) {
                return selectedExtras(product, request).stream()
                        .map(ProductExtra::getName).collect(Collectors.joining(", "));
        }

        private boolean isOnlinePaymentMethod(Long paymentMethodId) {
                return paymentMethodRepository.findById(paymentMethodId)
                                .map(pm -> "NIUBIZ".equalsIgnoreCase(pm.getProvider())
                                                || "MERCADOPAGO".equalsIgnoreCase(pm.getProvider()))
                                .orElse(false);
        }

        private boolean isOnlineOrderPendingPayment(Order order, OrderRequest request) {
                if (request.getPayments() == null || request.getPayments().isEmpty())
                        return false;
                return request.getPayments().stream().anyMatch(p -> isOnlinePaymentMethod(p.getPaymentMethodId()));
        }

        private void calculateDeliveryEstimate(Order order, OrderRequest request) {
                if (order.getType() != null
                                && "DELIVERY".equalsIgnoreCase(order.getType().getCode())) {
                        if (order.getDeliveryLatitude() == null || order.getDeliveryLongitude() == null) {
                                throw new IllegalArgumentException("La ubicación de delivery es obligatoria.");
                        }
                        // Falla de forma segura si Maps no responde o no existe ruta:
                        // no guardar órdenes fuera de cobertura o sin cotización.
                        DeliveryQuoteResponse quote = deliveryQuoteService.quote(
                                        order.getDeliveryLatitude(), order.getDeliveryLongitude());
                        order.setEstimatedDistance(quote.distanceKm() + " km");
                        order.setEstimatedDuration(quote.durationMinutes() + " min");
                        order.setEstimatedTime(quote.durationMinutes());
                }
        }

        public int countOrdersByDate(LocalDate date) {
                LocalDateTime startOfDay = date.atStartOfDay();
                LocalDateTime endOfDay = date.atTime(23, 59, 59);
                return orderRepository.countOrdersByDate(startOfDay, endOfDay);
        }

        public BigDecimal calculateSalesByDate(LocalDate date) {
                LocalDateTime startOfDay = date.atStartOfDay();
                LocalDateTime endOfDay = date.atTime(23, 59, 59);
                return orderRepository.calculateSalesByDate(startOfDay, endOfDay);
        }

        public Map<LocalDate, Integer> countOrdersLast7Days() {
                Map<LocalDate, Integer> map = new LinkedHashMap<>();
                for (int i = 6; i >= 0; i--) {
                        LocalDate day = LocalDate.now().minusDays(i);
                        LocalDateTime startOfDay = day.atStartOfDay();
                        LocalDateTime endOfDay = day.atTime(23, 59, 59);
                        int count = orderRepository.countOrdersByDate(startOfDay, endOfDay);
                        map.put(day, count);
                }
                return map;
        }

        public Map<LocalDate, BigDecimal> calculateSalesLast7Days() {
                Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
                for (int i = 6; i >= 0; i--) {
                        LocalDate day = LocalDate.now().minusDays(i);
                        LocalDateTime startOfDay = day.atStartOfDay();
                        LocalDateTime endOfDay = day.atTime(23, 59, 59);
                        BigDecimal sales = orderRepository.calculateSalesByDate(startOfDay, endOfDay);
                        map.put(day, sales != null ? sales : BigDecimal.ZERO);
                }
                return map;
        }

        public Long countAllOrders() {
                return orderRepository.count();
        }

        public BigDecimal calculateTotalSales() {
                List<String> validStatuses = List.of("COMPLETED", "DELIVERED", "PAID");
                BigDecimal total = orderRepository.sumTotalByStatusCodes(validStatuses);
                return total != null ? total : BigDecimal.ZERO;
        }

        public List<OrderTypeReportResponse> getOrderTypeStatistics(String lang) {
                return orderRepository.findOrderTypeStatistics(lang)
                                .stream()
                                .map(rowObj -> {
                                        Object[] row = (Object[]) rowObj;

                                        String orderTypeName = row[0] != null ? row[0].toString() : "DESCONOCIDO";
                                        Long totalOrders = ((Number) row[1]).longValue();
                                        BigDecimal totalRevenue = row[2] instanceof BigDecimal
                                                        ? (BigDecimal) row[2]
                                                        : new BigDecimal(row[2].toString());

                                        return OrderTypeReportResponse.builder()
                                                        .orderTypeName(orderTypeName)
                                                        .totalOrders(totalOrders.intValue())
                                                        .totalRevenue(totalRevenue)
                                                        .build();
                                })
                                .toList();
        }
}
