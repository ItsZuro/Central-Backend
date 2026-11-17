package com.centrral.centralres.features.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.centrral.centralres.core.config.ReservationProperties;
import com.centrral.centralres.features.customers.enums.ReservationStatus;
import com.centrral.centralres.features.customers.model.Reservation;
import com.centrral.centralres.features.customers.repository.ReservationRepository;
import com.centrral.centralres.features.orders.repository.OrderRepository;
import com.centrral.centralres.features.restaurant.enums.TableStatus;
import com.centrral.centralres.features.restaurant.model.TableEntity;
import com.centrral.centralres.features.restaurant.repository.TableRepository;

class TableServiceTest {
    @Mock private TableRepository tables;
    @Mock private ReservationRepository reservations;
    @Mock private ReservationProperties settings;
    @Mock private OrderRepository orders;
    private TableService service;
    private TableEntity table;
    private final LocalDate day = LocalDate.of(2030, 5, 2);

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        service = new TableService(tables, settings, reservations, orders);
        table = TableEntity.builder().id(1L).code("A1")
                .capacity(4).minCapacity(1).optimalCapacity(4).priority(1)
                .openTime(LocalTime.of(12, 0)).closeTime(LocalTime.of(22, 0))
                .reservationDurationMinutes(60).bufferBeforeMinutes(10).bufferAfterMinutes(15)
                .status(TableStatus.FREE).build();
        when(tables.findById(1L)).thenReturn(Optional.of(table));
    }

    @Test
    void pendingReservationBlocksOverlappingTime() {
        Reservation existing = Reservation.builder().table(table).reservationDate(day)
                .reservationTime(LocalTime.of(13, 0)).status(ReservationStatus.PENDING).build();
        when(reservations.findByTable_IdAndReservationDate(1L, day)).thenReturn(List.of(existing));
        assertThat(service.isTableAvailable(1L, "2030-05-02T13:30", 2, true)).isFalse();
    }

    @Test
    void cancelledReservationDoesNotBlockNewReservation() {
        Reservation existing = Reservation.builder().table(table).reservationDate(day)
                .reservationTime(LocalTime.of(13, 0)).status(ReservationStatus.CANCELLED).build();
        when(reservations.findByTable_IdAndReservationDate(1L, day)).thenReturn(List.of(existing));
        assertThat(service.isTableAvailable(1L, "2030-05-02T13:30", 2, true)).isTrue();
    }

    @Test
    void respectsBufferAndClosingHours() {
        assertThat(service.isTableAvailable(1L, "2030-05-02T12:00", 2, true)).isFalse();
        assertThat(service.isTableAvailable(1L, "2030-05-02T21:30", 2, true)).isFalse();
    }

    @Test
    void excludesOutOfServiceTablesAndExcessCapacity() {
        table.setStatus(TableStatus.OUT_OF_SERVICE);
        assertThat(service.isTableAvailable(1L, "2030-05-02T15:00", 2, true)).isFalse();
        table.setStatus(TableStatus.FREE);
        assertThat(service.isTableAvailable(1L, "2030-05-02T15:00", 5, true)).isFalse();
    }
}
