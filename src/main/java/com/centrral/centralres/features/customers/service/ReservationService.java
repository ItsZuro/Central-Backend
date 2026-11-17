package com.centrral.centralres.features.customers.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centrral.centralres.core.config.ReservationProperties;
import com.centrral.centralres.core.dto.response.PagedResponse;
import com.centrral.centralres.core.exceptions.InvalidReservationException;
import com.centrral.centralres.core.exceptions.ResourceNotFoundException;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.features.customers.dto.reservation.request.AuthenticatedReservationRequest;
import com.centrral.centralres.features.customers.dto.reservation.request.BaseReservationRequest;
import com.centrral.centralres.features.customers.dto.reservation.request.ReservationRequest;
import com.centrral.centralres.features.customers.dto.reservation.response.ReservationResponse;
import com.centrral.centralres.features.customers.enums.PointHistoryEventType;
import com.centrral.centralres.features.customers.enums.ReservationStatus;
import com.centrral.centralres.features.customers.model.Customer;
import com.centrral.centralres.features.customers.model.Reservation;
import com.centrral.centralres.features.customers.repository.CustomerRepository;
import com.centrral.centralres.features.customers.repository.ReservationRepository;
import com.centrral.centralres.features.feedbackloyalty.service.LoyaltyService;
import com.centrral.centralres.features.feedbackloyalty.service.PointsHistoryService;
import com.centrral.centralres.features.restaurant.enums.TableStatus;
import com.centrral.centralres.features.restaurant.model.TableEntity;
import com.centrral.centralres.features.restaurant.repository.TableRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final CustomerRepository customerRepository;
    private final TableRepository tableRepository;
    private final LoyaltyService loyaltyService;
    private final PointsHistoryService pointsHistoryService;
    private final ReservationProperties reservationProperties;

    private static final ZoneId RESTAURANT_ZONE = ZoneId.of("America/Lima");

    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED_TRANSITIONS = Map.of(
            ReservationStatus.PENDING, Set.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED),
            ReservationStatus.CONFIRMED, Set.of(ReservationStatus.COMPLETED, ReservationStatus.CANCELLED));

    /* -------------------- CREATE -------------------- */
    @Transactional
    public ReservationResponse createReservation(ReservationRequest dto) {
        Customer customer = findCustomerById(dto.getCustomerId());
        TableEntity table;

        if (dto.getTableId() == null) {
            table = findBestTableForNumberOfPeople(dto.getNumberOfPeople(), dto.getReservationDate(),
                    dto.getReservationTime());
            dto.setTableId(table.getId());
        } else {
            table = findTableById(dto.getTableId());
        }

        // Serialize concurrent bookings on the same table across ECS tasks.
        table = findTableByIdForUpdate(table.getId());
        validateReservationFields(dto, table, null, false);

        Reservation reservation = Reservation.builder()
                .customer(customer)
                .table(table)
                .status(dto.getStatus() != null ? dto.getStatus() : ReservationStatus.PENDING)
                .build();

        reservation.updateFromDto(dto, customer, table);
        Reservation saved = reservationRepository.save(reservation);
        publishReservationNotification(saved, "creada");

        // Confirmation is not the same as physical occupancy.

        log.info("✅ Reserva creada: id={}, cliente={}, mesa={}, fecha={}, hora={}, status={}",
                saved.getId(), customer.getId(), table.getCode(),
                saved.getReservationDate(), saved.getReservationTime(), saved.getStatus());

        return mapToResponse(saved);
    }

    @Transactional
    public ReservationResponse createWalkInReservation(Long customerId, Long tableId, int numberOfPeople,
            boolean sendEmail) {
        Customer customer = findCustomerById(customerId);

        TableEntity table;
        ZonedDateTime now = ZonedDateTime.now(RESTAURANT_ZONE).truncatedTo(ChronoUnit.MINUTES);
        LocalTime startTime;

        if (tableId == null) {
            table = findBestTableForWalkIn(numberOfPeople, now.toLocalDate());
            startTime = now.toLocalTime().isBefore(table.getOpenTime()) ? table.getOpenTime() : now.toLocalTime();
        } else {
            table = findTableById(tableId);

            startTime = now.toLocalTime().isBefore(table.getOpenTime()) ? table.getOpenTime() : now.toLocalTime();
            LocalTime reservationEnd = startTime
                    .plusMinutes(table.getReservationDurationMinutes() + table.getBufferAfterMinutes());

            if (reservationEnd.isAfter(table.getCloseTime())) {
                throw new InvalidReservationException(
                        "No se puede hacer walk-in en este momento, la mesa cierra a las " + table.getCloseTime());
            }
        }

        ReservationRequest dto = new ReservationRequest();
        dto.setCustomerId(customerId);
        dto.setTableId(table.getId());
        dto.setNumberOfPeople(numberOfPeople);
        dto.setReservationDate(now.toLocalDate());
        dto.setReservationTime(startTime);
        dto.setStatus(ReservationStatus.CONFIRMED);

        validateReservationFields(dto, table, null, true);

        // Crear reserva
        Reservation reservation = Reservation.builder()
                .customer(customer)
                .table(table)
                .status(ReservationStatus.CONFIRMED)
                .build();
        reservation.updateFromDto(dto, customer, table);
        Reservation saved = reservationRepository.save(reservation);

        if (sendEmail) {
            publishReservationNotification(saved, "creada");
        }

        // Marcar mesa ocupada
        table.setStatus(TableStatus.OCCUPIED);
        tableRepository.save(table);

        log.info("🚶 Walk-in creado: id={}, cliente={}, mesa={}, fecha={}, hora={}",
                saved.getId(),
                saved.getCustomer().getUser().getFullName(),
                saved.getTable().getCode(),
                saved.getReservationDate(),
                saved.getReservationTime());

        return mapToResponse(saved);
    }

    /**
     * Walk-in con asignación automática de mesa.
     */
    @Transactional
    public ReservationResponse createAutoWalkInReservation(Long customerId, int numberOfPeople, boolean sendEmail) {
        Customer customer = findCustomerById(customerId);
        ZonedDateTime now = ZonedDateTime.now(RESTAURANT_ZONE).truncatedTo(ChronoUnit.MINUTES);

        TableEntity table = findBestTableForWalkIn(numberOfPeople, now.toLocalDate());

        ReservationRequest dto = new ReservationRequest();
        dto.setCustomerId(customerId);
        dto.setTableId(table.getId());
        dto.setNumberOfPeople(numberOfPeople);
        dto.setReservationDate(now.toLocalDate());
        dto.setReservationTime(now.toLocalTime());
        dto.setStatus(ReservationStatus.CONFIRMED);

        validateReservationFields(dto, table, null, true); // true = walk-in

        Reservation reservation = Reservation.builder()
                .customer(customer)
                .table(table)
                .status(ReservationStatus.CONFIRMED)
                .build();
        reservation.updateFromDto(dto, customer, table);
        Reservation saved = reservationRepository.save(reservation);

        if (sendEmail) {
            publishReservationNotification(saved, "creada");
        }

        table.setStatus(TableStatus.OCCUPIED);
        tableRepository.save(table);

        log.info("🚀 Walk-in automático creado: id={}, cliente={}, mesa={}, fecha={}, hora={}",
                saved.getId(),
                saved.getCustomer().getUser().getFullName(),
                saved.getTable().getCode(),
                saved.getReservationDate(),
                saved.getReservationTime());

        return mapToResponse(saved);
    }

    /* -------------------- READ -------------------- */
    @Transactional(readOnly = true)
    public ReservationResponse getReservation(Long id) {
        return mapToResponse(findReservationById(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReservationResponse> getReservationsByCustomer(Long customerId, Pageable pageable) {
        Page<Reservation> page = reservationRepository.findByCustomerId(customerId, pageable);
        List<ReservationResponse> content = page.getContent()
                .stream()
                .map(this::mapToResponse)
                .toList();
        return buildPagedResponse(page, content);
    }

    /**
     * 🔹 Trae las reservas SOLO del cliente autenticado.
     */
    @Transactional(readOnly = true)
    public PagedResponse<ReservationResponse> getReservationsForAuthenticatedUser(Pageable pageable) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();

        Customer customer = customerRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado para usuario autenticado"));

        Page<Reservation> page = reservationRepository.findByCustomerId(customer.getId(), pageable);
        List<ReservationResponse> content = page.getContent()
                .stream()
                .map(this::mapToResponse)
                .toList();

        return buildPagedResponse(page, content);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReservationResponse> getReservationsByDateRange(
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {

        if (startDate == null && endDate == null) {
            throw new IllegalArgumentException("Debe especificar al menos una fecha (inicio o fin)");
        }

        if (endDate == null) {
            endDate = startDate;
        } else if (startDate == null) {
            startDate = endDate;
        }

        List<Reservation> reservations = reservationRepository.findByReservationDateBetween(startDate, endDate,
                pageable);
        List<ReservationResponse> responseList = reservations.stream()
                .map(this::mapToResponse)
                .toList();

        long total = reservationRepository.countByReservationDateBetween(startDate, endDate);

        int totalPages = (int) Math.ceil((double) total / pageable.getPageSize());
        boolean last = pageable.getPageNumber() + 1 >= totalPages;

        return new PagedResponse<>(
                responseList,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                total,
                totalPages,
                last);
    }

    /**
     * 🔹 Trae TODAS las reservas (modo admin/gestión).
     */
    @Transactional(readOnly = true)
    public PagedResponse<ReservationResponse> getAllReservations(Pageable pageable) {
        Page<Reservation> page = reservationRepository.findAll(pageable);
        List<ReservationResponse> content = page.getContent()
                .stream()
                .map(this::mapToResponse)
                .toList();

        return buildPagedResponse(page, content);
    }

    /* -------------------- UPDATE -------------------- */
    @Transactional
    public ReservationResponse updateReservation(Long id, ReservationRequest dto) {
        Reservation reservation = findReservationById(id);
        Customer customer = findCustomerById(dto.getCustomerId());
        TableEntity newTable = findTableById(dto.getTableId());

        // Validación
        validateReservationFields(dto, newTable, id, false);

        Reservation oldReservation = mapToReservationCopy(reservation); // Hacemos copia antes de actualizar

        reservation.updateFromDto(dto, customer, newTable);
        Reservation updated = reservationRepository.save(reservation);

        if (isSignificantChange(oldReservation, updated)) {
            publishReservationNotification(updated, "actualizada");
        }

        log.info("✏️ Reserva actualizada: id={}, cliente={}, mesa={}, fecha={}, hora={}",
                updated.getId(), customer.getId(), newTable.getCode(),
                updated.getReservationDate(), updated.getReservationTime());

        return mapToResponse(updated);
    }

    /* -------------------- COMPLETE -------------------- */
    @Transactional
    public ReservationResponse completeReservation(Long reservationId) {
        Reservation reservation = findReservationById(reservationId);
        validateStateTransition(reservation, ReservationStatus.COMPLETED);

        reservation.setStatus(ReservationStatus.COMPLETED);
        reservationRepository.save(reservation);

        TableEntity table = reservation.getTable();
        table.setStatus(TableStatus.FREE);
        tableRepository.save(table);

        Customer customer = reservation.getCustomer();

        int points = loyaltyService.calculatePoints(
                customer,
                null,
                "Reserva completada",
                reservation.getNumberOfPeople());

        if (points > 0) {
            pointsHistoryService.applyPoints(customer, points, PointHistoryEventType.EARNING);
        }

        log.info("🏁 Reserva completada: id={}, cliente={}, mesa={}, fecha={}, hora={}, puntos={}",
                reservation.getId(), customer.getId(), table.getCode(),
                reservation.getReservationDate(), reservation.getReservationTime(), points);

        return mapToResponse(reservation);
    }

    @Transactional
    public ReservationResponse confirmReservation(Long reservationId) {
        Reservation reservation = findReservationById(reservationId);
        validateStateTransition(reservation, ReservationStatus.CONFIRMED);

        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);
        publishReservationNotification(reservation, "confirmada");

        // A future reservation should not set the physical table to occupied.
        TableEntity table = reservation.getTable();

        log.info("✅ Reserva confirmada: id={}, cliente={}, mesa={}, fecha={}, hora={}, status={}",
                reservation.getId(),
                reservation.getCustomer().getId(),
                table.getCode(),
                reservation.getReservationDate(),
                reservation.getReservationTime(),
                reservation.getStatus());

        return mapToResponse(reservation);
    }

    /* -------------------- CANCEL -------------------- */
    @Transactional
    public ReservationResponse cancelReservationForAuthenticatedUser(Long reservationId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();
        Customer customer = customerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        if (!reservationRepository.existsByIdAndCustomer_Id(reservationId, customer.getId())) {
            throw new ResourceNotFoundException("Reserva no encontrada");
        }
        return cancelReservation(reservationId);
    }

    @Transactional
    public ReservationResponse cancelReservation(Long reservationId) {
        Reservation reservation = findReservationById(reservationId);
        validateStateTransition(reservation, ReservationStatus.CANCELLED);

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        publishReservationNotification(reservation, "cancelada");

        // A future booking does not occupy the physical table; preserve its real status.
        TableEntity table = reservation.getTable();

        log.info("❌ Reserva cancelada: id={}, cliente={}, mesa={}, fecha={}, hora={}",
                reservation.getId(), reservation.getCustomer().getId(), table.getCode(),
                reservation.getReservationDate(), reservation.getReservationTime());

        return mapToResponse(reservation);
    }

    /* -------------------- DELETE -------------------- */
    @Transactional
    public void deleteReservation(Long id) {
        Reservation reservation = findReservationById(id);
        reservationRepository.delete(reservation);
        log.info("🗑️ Reserva eliminada: id={}, cliente={}", id, reservation.getCustomer().getId());
    }

    /* -------------------- VALIDATIONS -------------------- */
    private void validateReservationFields(BaseReservationRequest dto, TableEntity table, Long currentReservationId,
            boolean isWalkIn) {

        if (dto.getNumberOfPeople() <= 0)
            throw new InvalidReservationException("El número de personas debe ser mayor a 0");

        if (!table.canAccommodate(dto.getNumberOfPeople()))
            throw new InvalidReservationException("La mesa no admite esa cantidad de personas");
        if (table.getStatus() == TableStatus.OUT_OF_SERVICE)
            throw new InvalidReservationException("La mesa está fuera de servicio");

        // Fallback de buffers
        int bufferBefore = table.getBufferBeforeMinutes() != null
                ? table.getBufferBeforeMinutes()
                : reservationProperties.getBufferBeforeMinutes();
        int bufferAfter = table.getBufferAfterMinutes() != null
                ? table.getBufferAfterMinutes()
                : reservationProperties.getBufferAfterMinutes();

        ZonedDateTime now = ZonedDateTime.now(RESTAURANT_ZONE);
        ZonedDateTime reservationDateTime = ZonedDateTime.of(dto.getReservationDate(), dto.getReservationTime(),
                RESTAURANT_ZONE);

        // Validación de anticipación mínima
        if (isWalkIn && reservationDateTime.isBefore(now.minusMinutes(5))) {
            throw new InvalidReservationException("La reserva no puede estar en el pasado (más de 5 minutos)");
        }
        if (!isWalkIn && reservationDateTime.isBefore(now.plusMinutes(15))) {
            throw new InvalidReservationException("Las reservas deben hacerse con al menos 15 minutos de anticipación");
        }

        // Horario de la mesa con buffers
        ZonedDateTime reservationStart = reservationDateTime.minusMinutes(bufferBefore);
        ZonedDateTime reservationEnd = reservationDateTime
                .plusMinutes(table.getReservationDurationMinutes() + bufferAfter);

        ZonedDateTime tableOpen = ZonedDateTime.of(dto.getReservationDate(), table.getOpenTime(), RESTAURANT_ZONE);
        ZonedDateTime tableClose = ZonedDateTime.of(dto.getReservationDate(), table.getCloseTime(), RESTAURANT_ZONE);

        if (reservationStart.isBefore(tableOpen) || reservationEnd.isAfter(tableClose)) {
            throw new InvalidReservationException("La reserva debe estar dentro del horario de la mesa: "
                    + table.getOpenTime() + " - " + table.getCloseTime());
        }

        // Comprobar solapamiento con otras reservas
        List<Reservation> reservations = reservationRepository.findByTable_IdAndReservationDate(table.getId(),
                dto.getReservationDate());

        for (Reservation r : reservations) {
            if (currentReservationId != null && r.getId().equals(currentReservationId))
                continue;
            if (r.getStatus() != ReservationStatus.PENDING && r.getStatus() != ReservationStatus.CONFIRMED)
                continue;

            int existingBufferBefore = r.getTable().getBufferBeforeMinutes() != null
                    ? r.getTable().getBufferBeforeMinutes()
                    : reservationProperties.getBufferBeforeMinutes();
            int existingBufferAfter = r.getTable().getBufferAfterMinutes() != null
                    ? r.getTable().getBufferAfterMinutes()
                    : reservationProperties.getBufferAfterMinutes();

            ZonedDateTime existingStart = ZonedDateTime.of(r.getReservationDate(), r.getReservationTime(),
                    RESTAURANT_ZONE).minusMinutes(existingBufferBefore);
            ZonedDateTime existingEnd = ZonedDateTime.of(r.getReservationDate(), r.getReservationTime(),
                    RESTAURANT_ZONE).plusMinutes(r.getTable().getReservationDurationMinutes() + existingBufferAfter);

            boolean overlaps = reservationStart.isBefore(existingEnd) && reservationEnd.isAfter(existingStart);
            if (overlaps) {
                throw new InvalidReservationException(
                        "La mesa ya está ocupada en ese intervalo de tiempo (incluyendo tiempo de limpieza y preparación)");
            }
        }
    }

    private void validateStateTransition(Reservation reservation, ReservationStatus newStatus) {
        if (ALLOWED_TRANSITIONS.getOrDefault(reservation.getStatus(), Set.of()).contains(newStatus))
            return;
        throw new InvalidReservationException("No se puede cambiar de " + reservation.getStatus() + " a " + newStatus);
    }

    /* -------------------- HELPERS -------------------- */
    private Reservation findReservationById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
    }

    private TableEntity findTableByIdForUpdate(Long id) {
        return tableRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));
    }

    private Customer findCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
    }

    private TableEntity findTableById(Long id) {
        return tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));
    }

    /**
     * Encuentra la mejor mesa libre para walk-in según número de personas y fecha.
     */
    private TableEntity findBestTableForWalkIn(int numberOfPeople, LocalDate date) {
        ZonedDateTime now = ZonedDateTime.now(RESTAURANT_ZONE).truncatedTo(ChronoUnit.MINUTES);

        return tableRepository.findAll().stream()
                .filter(t -> t.getStatus() == TableStatus.FREE)
                .filter(t -> numberOfPeople >= t.getMinCapacity() && numberOfPeople <= t.getCapacity())
                .sorted(Comparator.comparingInt(TableEntity::getCapacity))
                .filter(table -> {
                    LocalTime startTime = now.toLocalTime().isBefore(table.getOpenTime()) ? table.getOpenTime()
                            : now.toLocalTime();
                    ReservationRequest dto = new ReservationRequest();
                    dto.setNumberOfPeople(numberOfPeople);
                    dto.setReservationDate(date);
                    dto.setReservationTime(startTime);
                    try {
                        validateReservationFields(dto, table, null, true); // true = walk-in
                        return true;
                    } catch (InvalidReservationException ignored) {
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow(
                        () -> new InvalidReservationException("No hay mesas disponibles para walk-in en este momento"));
    }

    @Transactional
    public ReservationResponse createReservationForAuthenticatedUser(AuthenticatedReservationRequest dto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();

        // Buscar el Customer por su usuario
        Customer customer = customerRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado para usuario autenticado"));

        TableEntity table;

        if (dto.getTableId() == null) {
            table = findBestTableForNumberOfPeople(
                    dto.getNumberOfPeople(),
                    dto.getReservationDate(),
                    dto.getReservationTime());
            dto.setTableId(table.getId());
        } else {
            table = findTableById(dto.getTableId());
        }

        // Validaciones
        // Serialize concurrent bookings on the same table across ECS tasks.
        table = findTableByIdForUpdate(table.getId());
        validateReservationFields(dto, table, null, false);

        Reservation reservation = Reservation.builder()
                .customer(customer)
                .table(table)
                .status(ReservationStatus.PENDING) // 👈 siempre arranca en pending
                .build();

        reservation.updateFromDto(dto, customer, table);
        Reservation saved = reservationRepository.save(reservation);

        publishReservationNotification(saved, "creada");

        // Confirmation is not the same as physical occupancy.

        log.info("✅ Reserva creada por usuario autenticado: id={}, cliente={}, mesa={}, fecha={}, hora={}, status={}",
                saved.getId(), customer.getId(), table.getCode(),
                saved.getReservationDate(), saved.getReservationTime(), saved.getStatus());

        return mapToResponse(saved);
    }

    /**
     * Encuentra la mejor mesa libre para el número de personas y horario indicado.
     */
    private TableEntity findBestTableForNumberOfPeople(int numberOfPeople, LocalDate date, LocalTime time) {
        return tableRepository.findAll().stream()
                .filter(t -> t.getStatus() != TableStatus.OUT_OF_SERVICE)
                .filter(t -> numberOfPeople >= t.getMinCapacity() && numberOfPeople <= t.getCapacity())
                .sorted(Comparator.comparingInt(TableEntity::getCapacity))
                .filter(table -> {
                    ReservationRequest dto = new ReservationRequest();
                    dto.setNumberOfPeople(numberOfPeople);
                    dto.setReservationDate(date);
                    dto.setReservationTime(time);
                    try {
                        validateReservationFields(dto, table, null, false);
                        return true;
                    } catch (InvalidReservationException ignored) {
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow(() -> new InvalidReservationException(
                        "No hay mesas disponibles para " + numberOfPeople + " personas en ese horario"));
    }

    private void publishReservationNotification(Reservation reservation, String action) {
        try {
            String tableAlias = reservation.getTable().getAlias();
            String tableCode = reservation.getTable().getCode();

            // ReservationNotificationEvent event = ReservationNotificationEvent.builder()
            // .userId(reservation.getCustomer().getId())
            // .recipient(reservation.getCustomer().getUser().getEmail())
            // .subject("Reserva " + action + " - " + tableAlias)
            // .message("Hola " + reservation.getCustomer().getUser().getFullName() +
            // ",\n\n" +
            // "Tu reserva para " + reservation.getNumberOfPeople() + " personas en la " +
            // tableAlias + " (" + tableCode + ") ha sido " + action.toLowerCase() + ".\n" +
            // "Fecha: " + reservation.getReservationDate() + "\n" +
            // "Hora: " + reservation.getReservationTime() + "\n\n" +
            // "¡Gracias por elegirnos!")
            // .reservationId(reservation.getId())
            // .reservationDate(
            // LocalDateTime.of(reservation.getReservationDate(),
            // reservation.getReservationTime()))
            // .reservationTime(reservation.getReservationTime().toString())
            // .numberOfPeople(reservation.getNumberOfPeople())
            // .customerName(reservation.getCustomer().getUser().getFullName())
            // .tableName(tableAlias) // aquí mejor mostrar alias
            // .actionUrl("https://miapp.com/reservations/" + reservation.getId())
            // .build();

            // notificationProducer.send("notifications", event);
        } catch (Exception e) {
            log.error("❌ Error al publicar notificación de reserva", e);
        }
    }

    private boolean isSignificantChange(Reservation oldRes, Reservation updatedRes) {
        boolean statusChanged = !oldRes.getStatus().equals(updatedRes.getStatus());
        boolean dateChanged = !oldRes.getReservationDate().equals(updatedRes.getReservationDate());
        boolean timeChanged = !oldRes.getReservationTime().equals(updatedRes.getReservationTime());

        Long oldTableId = oldRes.getTable() != null ? oldRes.getTable().getId() : null;
        Long newTableId = updatedRes.getTable() != null ? updatedRes.getTable().getId() : null;
        boolean tableChanged = (oldTableId == null ? newTableId != null : !oldTableId.equals(newTableId));

        return statusChanged || dateChanged || timeChanged || tableChanged;
    }

    private Reservation mapToReservationCopy(Reservation res) {
        Reservation copy = new Reservation();
        copy.setStatus(res.getStatus());
        copy.setReservationDate(res.getReservationDate());
        copy.setReservationTime(res.getReservationTime());
        copy.setTable(res.getTable());
        return copy;
    }

    private ReservationResponse mapToResponse(Reservation reservation) {
        return ReservationResponse.builder()
                .id(reservation.getId())
                .customerId(reservation.getCustomer() != null ? reservation.getCustomer().getId() : null)
                .customerName(
                        reservation.getCustomer() != null ? reservation.getCustomer().getUser().getFullName() : null)
                .tableId(reservation.getTable() != null ? reservation.getTable().getId() : null)
                .tableNumber(reservation.getTable() != null ? reservation.getTable().getCode() : null)
                .contactName(reservation.getContactName())
                .contactPhone(reservation.getContactPhone())
                .reservationDate(reservation.getReservationDate())
                .reservationTime(reservation.getReservationTime())
                .numberOfPeople(reservation.getNumberOfPeople())
                .status(reservation.getStatus())
                .build();
    }

    private <T> PagedResponse<T> buildPagedResponse(Page<?> page, List<T> content) {
        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    public int countReservationsByDate(LocalDate date) {
        return reservationRepository.countReservationsByDate(date);
    }

    public Map<LocalDate, Integer> countReservationsNext7Days() {
        Map<LocalDate, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = LocalDate.now().plusDays(i);
            int count = reservationRepository.countReservationsByDate(day);
            map.put(day, count);
        }
        return map;
    }

    public List<ReservationResponse> findUpcomingReservations() {
        return reservationRepository.findUpcomingReservations()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

}
