package com.centrral.centralres.features.customers.dto.customer.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerAccountStatusRequest {
    @NotNull(message = "Debes seleccionar el estado de la cuenta")
    private Boolean enabled;

    @NotBlank(message = "Es obligatorio justificar el cambio")
    @Size(min = 10, max = 500, message = "La justificación debe tener entre 10 y 500 caracteres")
    private String reason;
}
