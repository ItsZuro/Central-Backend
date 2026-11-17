package com.centrral.centralres.features.contact.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactRequest {
    @NotBlank(message = "El asunto es obligatorio")
    @Size(max = 120, message = "El asunto no debe superar 120 caracteres")
    private String subject;

    @NotBlank(message = "El mensaje es obligatorio")
    @Size(max = 3000, message = "El mensaje no debe superar 3000 caracteres")
    private String message;
}
