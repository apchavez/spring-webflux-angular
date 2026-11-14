package com.apchavez.products.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado de encolar una importación masiva de productos")
public record ImportJobResponseDTO(

        @Schema(description = "ID de la ejecución del job, usado para consultar su estado", example = "1")
        Long jobExecutionId,

        @Schema(description = "Estado inicial del job", example = "STARTING")
        String status) {
}
