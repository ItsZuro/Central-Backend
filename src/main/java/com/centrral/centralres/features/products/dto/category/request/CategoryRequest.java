package com.centrral.centralres.features.products.dto.category.request;

import jakarta.validation.constraints.NotBlank;
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
public class CategoryRequest {
    @NotBlank(message = "El nombre de la categoría es obligatorio")
    @Size(max = 650, message = "La descripción no puede superar los 650 caracteres")
    private String description;

    @Size(max = 2048, message = "La URL de imagen es demasiado larga")
    private String imageUrl;

    private String name;
}
