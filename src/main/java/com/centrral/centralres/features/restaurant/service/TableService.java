package com.centrral.centralres.features.restaurant.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.centrral.centralres.core.config.ReservationProperties;
import com.centrral.centralres.features.customers.model.Reservation;
import com.centrral.centralres.features.customers.enums.ReservationStatus;
import com.centrral.centralres.features.customers.repository.ReservationRepository;
import com.centrral.centralres.features.orders.model.Order;
import com.centrral.centralres.features.orders.repository.OrderRepository;
import com.centrral.centralres.features.restaurant.dto.table.request.TableRequest;
import com.centrral.centralres.features.restaurant.dto.table.response.TableAvailabilityResponse;
import com.centrral.centralres.features.restaurant.dto.table.response.TableResponse;
import com.centrral.centralres.features.restaurant.enums.TableStatus;
import com.centrral.centralres.features.restaurant.model.TableEntity;
import com.centrral.centralres.features.restaurant.repository.TableRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TableService {

    private final TableRepository tableRepository;
    private final ReservationProperties reservationProperties;
    private final ReservationRepository reservationRepository;
    private final OrderRepository orderRepository;

    public List<TableResponse> getAllTables() {
        List<TableEntity> tables = tableRepository.findAll();

        List<String> activeStatuses = List.of("PENDING", "PREPARING", "IN_PROGRESS", "READY_FOR_PICKUP", "PAID");
        List<Order> activeOrders = orderRepository.findActiveOrdersByType(activeStatuses, "DINE_IN");

        Map<Long, Long> activeOrderMap = activeOrders.stream()
                .filter(order -> order.getTable() != null)
                .collect(Collectors.toMap(
                        order -> order.getTable().getId(),
                        Order::getId,
                        (existing, replacement) -> existing));

        return tables.stream()
                .map(table -> mapToResponse(table, activeOrderMap.get(table.getId())))
                .collect(Collectors.toList());
    }

    public TableResponse getTableById(Long id) {
        TableEntity table = tableRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Mesa no encontrada con id: " + id));
        return mapToResponse(table, null);
    }

    public TableResponse createTable(TableRequest request) {
        validateTableRequest(request);
        TableEntity table = mapToEntity(request);
        table.setStatus(TableStatus.FREE);
        TableEntity savedTable = tableRepository.save(table);
        return mapToResponse(savedTable, null);
    }

    public TableResponse updateTable(Long id, TableRequest request) {
        validateTableRequest(request);
        TableEntity table = tableRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Mesa no encontrada con id: " + id));

        table.setCode(request.getCode());
        table.setAlias(request.getAlias());
        table.setCapacity(request.getCapacity());
        table.setMinCapacity(request.getMinCapacity());
        table.setOptimalCapacity(request.getOptimalCapacity());
        table.setPriority(request.getPriority());
        table.setDescription(request.getDescription());
        table.setOpenTime(request.getOpenTime());
        table.setCloseTime(request.getCloseTime());
        table.setReservationDurationMinutes(request.getReservationDurationMinutes());
        table.setBufferBeforeMinutes(request.getBufferBeforeMinutes());
        table.setBufferAfterMinutes(request.getBufferAfterMinutes());
        table.setStatus(request.getStatus());
        TableEntity updatedTable = tableRepository.save(table);

        return mapToResponse(updatedTable, null);
    }

    public void deleteTable(Long id) {
        TableEntity table = tableRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Mesa no encontrada con id: " + id));
        tableRepository.delete(table);
    }

    /* -------------------- DISPONIBILIDAD -------------------- */

    public boolean isTableAvailable(Long tableId, String startTime, int numberOfPeople, boolean includeBuffers) {
        TableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new EntityNotFoundException("Mesa no encontrada con id: " + tableId));
        return isAvailableAt(table, LocalDateTime.parse(startTime), numberOfPeople, includeBuffers);
    }

    private boolean isAvailableAt(TableEntity table, LocalDateTime requested,
            int numberOfPeople, boolean includeBuffers) {
        if (table.getStatus() == TableStatus.OUT_OF_SERVICE || !table.canAccommodate(numberOfPeople)) {
            return false;
        }
        if (requested.isBefore(LocalDateTime.now(ZoneId.of("America/Lima")).plusMinutes(15))) {
            return false;
        }

        int before = includeBuffers
                ? (table.getBufferBeforeMinutes() != null ? table.getBufferBeforeMinutes()
                        : reservationProperties.getBufferBeforeMinutes()) : 0;
        int after = includeBuffers
                ? (table.getBufferAfterMinutes() != null ? table.getBufferAfterMinutes()
                        : reservationProperties.getBufferAfterMinutes()) : 0;
        LocalDateTime start = requested.minusMinutes(before);
        LocalDateTime end = requested.plusMinutes(table.getReservationDurationMinutes() + after);
        LocalDateTime open = requested.toLocalDate().atTime(table.getOpenTime());
        LocalDateTime close = requested.toLocalDate().atTime(table.getCloseTime());
        if (start.isBefore(open) || end.isAfter(close)) return false;

        for (Reservation existing : reservationRepository.findByTable_IdAndReservationDate(
                table.getId(), requested.toLocalDate())) {
            if (existing.getStatus() != ReservationStatus.PENDING &&
                    existing.getStatus() != ReservationStatus.CONFIRMED) continue;
            int existingBefore = includeBuffers
                    ? (existing.getTable().getBufferBeforeMinutes() != null
                        ? existing.getTable().getBufferBeforeMinutes()
                        : reservationProperties.getBufferBeforeMinutes()) : 0;
            int existingAfter = includeBuffers
                    ? (existing.getTable().getBufferAfterMinutes() != null
                        ? existing.getTable().getBufferAfterMinutes()
                        : reservationProperties.getBufferAfterMinutes()) : 0;
            LocalDateTime booked = existing.getReservationDate().atTime(existing.getReservationTime());
            LocalDateTime bookedStart = booked.minusMinutes(existingBefore);
            LocalDateTime bookedEnd = booked.plusMinutes(
                    existing.getTable().getReservationDurationMinutes() + existingAfter);
            if (start.isBefore(bookedEnd) && end.isAfter(bookedStart)) return false;
        }
        return true;
    }

    /* -------------------- MESAS DISPONIBLES Y ÓPTIMA -------------------- */

    public List<TableResponse> getAvailableTables(int numberOfPeople, String startTime) {
        return tableRepository.findAll()
                .stream()
                .filter(t -> t.canAccommodate(numberOfPeople))
                .filter(t -> isTableAvailable(t.getId(), startTime, numberOfPeople, true))
                .map(table -> mapToResponse(table, null))
                .collect(Collectors.toList());
    }

    public TableResponse findOptimalTable(int numberOfPeople, String startTime) {
        return tableRepository.findAll()
                .stream()
                .filter(t -> t.canAccommodate(numberOfPeople))
                .filter(t -> isTableAvailable(t.getId(), startTime, numberOfPeople, true))
                .min(Comparator.comparingInt(t -> t.getOptimalCapacity()))
                .map(table -> mapToResponse(table, null))
                .orElseThrow(() -> new EntityNotFoundException(
                        "No hay mesas disponibles para este horario y número de personas"));
    }

    public List<TableAvailabilityResponse> getTablesWithAvailableTimes(int numberOfPeople, LocalDateTime date,
            boolean filterByCapacity) {
        List<TableAvailabilityResponse> results = new ArrayList<>();

        for (TableEntity table : tableRepository.findAll()) {

            if (filterByCapacity && !table.canAccommodate(numberOfPeople)) {
                log.info("Mesa {} descartada: no puede acomodar {} personas.", table.getCode(), numberOfPeople);
                continue;
            }

            List<String> availableTimes = calculateAvailableTimes(table, date);
            log.info("Mesa {} - Horarios calculados: {}", table.getCode(), availableTimes);

            TableAvailabilityResponse response = TableAvailabilityResponse.builder()
                    .id(table.getId())
                    .name(table.getCode())
                    .capacity(table.getCapacity())
                    .minCapacity(table.getMinCapacity())
                    .availableTimes(availableTimes)
                    .build();

            results.add(response);
        }

        log.info("TOTAL mesas retornadas: {}", results.size());
        return results;
    }

    private List<String> calculateAvailableTimes(TableEntity table, LocalDateTime date) {
        List<String> times = new ArrayList<>();
        LocalDateTime start = LocalDateTime.of(date.toLocalDate(), table.getOpenTime());
        LocalDateTime endOfDay = LocalDateTime.of(date.toLocalDate(), table.getCloseTime());
        int incrementMinutes = 30;

        while (!start.plusMinutes(table.getReservationDurationMinutes()).isAfter(endOfDay)) {
            if (isAvailableAt(table, start, table.getMinCapacity(), true)) {
                times.add(start.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            }
            start = start.plusMinutes(incrementMinutes);
        }

        return times;
    }

    /* -------------------- UTILIDADES -------------------- */

    private TableResponse mapToResponse(TableEntity table, Long activeOrderId) {
        TableResponse response = TableResponse.builder()
                .id(table.getId())
                .code(table.getCode())
                .alias(table.getAlias())
                .capacity(table.getCapacity())
                .minCapacity(table.getMinCapacity())
                .optimalCapacity(table.getOptimalCapacity())
                .priority(table.getPriority())
                .description(table.getDescription())
                .openTime(table.getOpenTime())
                .closeTime(table.getCloseTime())
                .reservationDurationMinutes(table.getReservationDurationMinutes())
                .bufferBeforeMinutes(table.getBufferBeforeMinutes())
                .bufferAfterMinutes(table.getBufferAfterMinutes())
                .status(table.getStatus())
                // No seteamos activeOrderId aquí
                .build();

        if (activeOrderId != null) {
            response.setActiveOrderId(activeOrderId);
            if (response.getStatus() == TableStatus.FREE) {
                response.setStatus(TableStatus.OCCUPIED);
            }
        } else if (table.getStatus() == TableStatus.OCCUPIED) {
        }

        return response;
    }

    private TableEntity mapToEntity(TableRequest request) {
        return TableEntity.builder()
                .code(request.getCode())
                .alias(request.getAlias())
                .capacity(request.getCapacity())
                .minCapacity(request.getMinCapacity())
                .optimalCapacity(request.getOptimalCapacity())
                .priority(request.getPriority())
                .description(request.getDescription())
                .openTime(request.getOpenTime())
                .closeTime(request.getCloseTime())
                .reservationDurationMinutes(request.getReservationDurationMinutes())
                .bufferBeforeMinutes(request.getBufferBeforeMinutes())
                .bufferAfterMinutes(request.getBufferAfterMinutes())
                .status(request.getStatus())
                .build();
    }

    private void validateTableRequest(TableRequest request) {
        if (request.getMinCapacity() > request.getOptimalCapacity()) {
            throw new IllegalArgumentException("La capacidad mínima no puede ser mayor que la capacidad óptima");
        }
        if (request.getOptimalCapacity() > request.getCapacity()) {
            throw new IllegalArgumentException("La capacidad óptima no puede ser mayor que la capacidad máxima");
        }
        if (request.getOpenTime().isAfter(request.getCloseTime())
                || request.getOpenTime().equals(request.getCloseTime())) {
            throw new IllegalArgumentException("La hora de apertura debe ser anterior a la hora de cierre");
        }
        if (request.getReservationDurationMinutes() <= 0) {
            throw new IllegalArgumentException("La duración de la reserva debe ser mayor a 0");
        }
        if (request.getBufferBeforeMinutes() < 0 || request.getBufferAfterMinutes() < 0) {
            throw new IllegalArgumentException("Los buffers no pueden ser negativos");
        }
    }

}
