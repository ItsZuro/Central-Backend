package com.centrral.centralres.features.customers.service;

import java.time.LocalDate;
import java.util.List;

import com.centrral.centralres.core.exceptions.InvalidReservationException;
import com.centrral.centralres.features.customers.model.Reservation;

/**
 * Límites de reservas hechos directamente por clientes en la web.
 * No se aplica a la gestión administrativa ni a reservas walk-in.
 */
public final class SelfServiceReservationPolicy {
    public static final int MAX_UPCOMING = 2;
    public static final int MAX_SAME_DAY = 1;

    private SelfServiceReservationPolicy() {}

    public static void validate(List<Reservation> activeUpcoming, LocalDate requestedDate) {
        if (requestedDate == null) {
            throw new InvalidReservationException("Selecciona una fecha para tu reserva.");
        }
        if (activeUpcoming.stream().anyMatch(r -> requestedDate.equals(r.getReservationDate()))) {
            throw new InvalidReservationException(
                    "Ya tienes una reserva activa para esa fecha. "
                    + "Solo puedes tener 1 reserva por día. Si necesitas otra mesa, contacta al restaurante.");
        }
        if (activeUpcoming.size() >= MAX_UPCOMING) {
            throw new InvalidReservationException(
                    "Alcanzaste el límite de 2 reservas futuras activas por cuenta. "
                    + "Cancela una reserva que no utilizarás o contacta al restaurante para coordinar más.");
        }
    }
}
