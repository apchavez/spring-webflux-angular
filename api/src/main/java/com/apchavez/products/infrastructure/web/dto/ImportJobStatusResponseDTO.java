package com.apchavez.products.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Estado y resumen de una importación masiva de productos")
public record ImportJobStatusResponseDTO(

        @Schema(description = "ID de la ejecución del job", example = "1")
        Long jobExecutionId,

        @Schema(description = "Estado del job (STARTING, STARTED, COMPLETED, FAILED, ...)", example = "COMPLETED")
        String status,

        @Schema(description = "Filas leídas del CSV", example = "20")
        long readCount,

        @Schema(description = "Productos creados exitosamente", example = "18")
        long writeCount,

        @Schema(description = "Filas omitidas por errores", example = "2")
        long skipCount,

        @Schema(description = "Detalle de las filas omitidas")
        List<ImportRowErrorDTO> errors) {
}
