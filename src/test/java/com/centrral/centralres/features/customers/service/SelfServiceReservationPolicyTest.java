package com.centrral.centralres.features.customers.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.centrral.centralres.core.exceptions.InvalidReservationException;
import com.centrral.centralres.features.customers.model.Reservation;

class SelfServiceReservationPolicyTest {

    private static final LocalDate TOMORROW = LocalDate.of(2026, 12, 1);

    private Reservation activeOn(LocalDate date) {
        return Reservation.builder().reservationDate(date).build();
    }

    @Test
    void allowsFirstAndSecondBookingsOnDifferentDates() {
        assertThatCode(() -> SelfServiceReservationPolicy.validate(List.of(), TOMORROW))
                .doesNotThrowAnyException();
        assertThatCode(() -> SelfServiceReservationPolicy.validate(
                List.of(activeOn(TOMORROW)), TOMORROW.plusDays(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void preventsMultipleReservationsOnSameDayEvenWithDifferentTables() {
        assertThatThrownBy(() -> SelfServiceReservationPolicy.validate(
                List.of(activeOn(TOMORROW)), TOMORROW))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessageContaining("1 reserva por día");
    }

    @Test
    void preventsThirdUpcomingBooking() {
        assertThatThrownBy(() -> SelfServiceReservationPolicy.validate(
                List.of(activeOn(TOMORROW), activeOn(TOMORROW.plusDays(1))),
                TOMORROW.plusDays(2)))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessageContaining("2 reservas futuras");
    }

    @Test
    void rejectsMissingDate() {
        assertThatThrownBy(() -> SelfServiceReservationPolicy.validate(List.of(), null))
                .isInstanceOf(InvalidReservationException.class);
    }
}
