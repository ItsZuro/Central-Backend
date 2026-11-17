package com.centrral.centralres.features.customers.dto.reservation.request;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthenticatedReservationRequest implements BaseReservationRequest {

    @NotNull(message = "Mesa es obligatoria")
    private Long tableId;

    @NotBlank(message = "Nombre de contacto es obligatorio")
    @Size(max = 100, message = "Nombre de contacto no debe exceder los 100 caracteres")
    private String contactName;

    @NotBlank(message = "Teléfono de contacto es obligatorio")
    @Size(min = 7, max = 15, message = "Teléfono de contacto debe tener entre 7 y 15 caracteres")
    private String contactPhone;

    @NotNull(message = "Fecha de reserva es obligatoria")
    private LocalDate reservationDate;

    @NotNull(message = "Hora de reserva es obligatoria")
    private LocalTime reservationTime;

    @NotNull(message = "Número de personas es obligatorio")
    @Min(value = 1, message = "Minimo 1 persona por reserva")
    @Max(value = 50, message = "Numero máximo de personas por reserva es 50")
    private Integer numberOfPeople;
}