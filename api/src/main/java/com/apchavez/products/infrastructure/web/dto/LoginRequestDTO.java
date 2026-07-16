package com.apchavez.products.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciales de login")
public record LoginRequestDTO(

        @NotBlank(message = "El usuario es requerido")
        @Schema(description = "Usuario demo", example = "admin")
        String username,

        @NotBlank(message = "La contraseña es requerida")
        @Schema(description = "Contraseña demo", example = "admin123")
        String password) {
}
