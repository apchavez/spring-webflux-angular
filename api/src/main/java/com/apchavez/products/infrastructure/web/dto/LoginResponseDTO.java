package com.apchavez.products.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

@Schema(description = "Resultado de un login exitoso")
public record LoginResponseDTO(

        @Schema(description = "JWT firmado RS256") String token,
        @Schema(description = "Tipo de token", example = "Bearer") String tokenType,
        @Schema(description = "Vigencia del token en segundos", example = "3600") long expiresIn,
        @Schema(description = "Usuario autenticado", example = "admin") String username,
        @Schema(description = "Roles del usuario", example = "[\"ADMIN\", \"USER\"]") Set<String> roles) {
}
