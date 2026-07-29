package com.apchavez.products.infrastructure.batch;

public record ProductCsvRow(
        String sku,
        String name,
        String description,
        String category,
        Double price,
        Integer stock,
        Boolean active) {
}
