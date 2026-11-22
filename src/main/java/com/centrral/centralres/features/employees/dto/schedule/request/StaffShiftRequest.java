package com.centrral.centralres.features.employees.dto.schedule.request;

import java.time.LocalTime;

import com.centrral.centralres.features.employees.enums.DayOfWeekEnum;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Un turno semanal del personal, expresado en la hora local de Perú (America/Lima). */
@Getter
@Setter
public class StaffShiftRequest {
    @NotNull(message = "El día de trabajo es obligatorio")
    private DayOfWeekEnum dayOfWeek;

    @NotNull(message = "La hora de entrada es obligatoria")
    private LocalTime startTime;

    @NotNull(message = "La hora de salida es obligatoria")
    private LocalTime endTime;
}
