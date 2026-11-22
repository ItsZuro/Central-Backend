package com.centrral.centralres.core.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * La contraseña del administrador confirma la operación sensible.
 * Nunca se devuelve la nueva contraseña en la respuesta.
 */
@Getter
@Setter
public class AdminResetStaffPasswordRequest {

    @NotBlank(message = "Confirma la contraseña del administrador")
    private String adminPassword;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 12, max = 128, message = "La contraseña debe tener entre 12 y 128 caracteres")
    private String newPassword;
}
