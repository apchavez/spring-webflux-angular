package com.apchavez.products.infrastructure.batch;

import org.springframework.batch.infrastructure.item.file.mapping.FieldSetMapper;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

/**
 * Deliberately not a {@link org.springframework.batch.infrastructure.item.file.mapping.BeanWrapperFieldSetMapper} —
 * its numeric/boolean coercion is too lenient (e.g. a malformed boolean silently becomes {@code false})
 * for a feature whose whole point is catching bad rows. Any exception thrown here propagates through
 * {@link org.springframework.batch.infrastructure.item.file.FlatFileItemReader}, which wraps it as a
 * {@link org.springframework.batch.infrastructure.item.file.FlatFileParseException} carrying the line number —
 * that's what gets registered as skippable in the step configuration.
 */
public class ProductCsvFieldSetMapper implements FieldSetMapper<ProductCsvRow> {

    @Override
    public ProductCsvRow mapFieldSet(FieldSet fieldSet) throws BindException {
        String sku = blankToNull(fieldSet.readString("sku"));
        String name = blankToNull(fieldSet.readString("name"));
        String description = blankToNull(fieldSet.readString("description"));
        String category = blankToNull(fieldSet.readString("category"));
        double price = fieldSet.readDouble("price");
        int stock = fieldSet.readInt("stock");
        boolean active = readStrictBoolean(fieldSet.readString("active"));

        return new ProductCsvRow(sku, name, description, category, price, stock, active);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static boolean readStrictBoolean(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("El campo 'active' no puede estar vacío");
        }
        String trimmed = raw.trim();
        if (trimmed.equalsIgnoreCase("true")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException("El campo 'active' debe ser 'true' o 'false', se recibió: '" + raw + "'");
    }
}
