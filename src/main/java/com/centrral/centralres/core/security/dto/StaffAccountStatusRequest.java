package com.centrral.centralres.core.security.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Activación y baja lógica de cuentas operativas de personal. */
@Getter
@Setter
public class StaffAccountStatusRequest {
    @NotNull(message = "Indica si la cuenta debe permanecer activa.")
    private Boolean enabled;
}
