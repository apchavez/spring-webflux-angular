package com.apchavez.products.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error de una fila individual omitida durante la importación")
public record ImportRowErrorDTO(

        @Schema(description = "Número de línea en el CSV (-1 si no aplica)", example = "5")
        int lineNumber,

        @Schema(description = "SKU de la fila, si pudo leerse", example = "SKU-100")
        String sku,

        @Schema(description = "Motivo por el cual se omitió la fila")
        String message) {
}
