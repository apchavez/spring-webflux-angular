package com.apchavez.products.infrastructure.batch;

import com.apchavez.products.domain.exception.InvalidProductException;
import com.apchavez.products.domain.model.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductImportItemProcessorTest {

    private final ProductImportItemProcessor processor = new ProductImportItemProcessor();

    @Test
    void mapsValidRowToProduct() {
        ProductCsvRow row = new ProductCsvRow("SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true);

        Product product = processor.process(row);

        assertThat(product.id()).isNull();
        assertThat(product.sku()).isEqualTo("SKU-001");
        assertThat(product.name()).isEqualTo("Wireless Mouse");
        assertThat(product.price()).isEqualTo(29.99);
        assertThat(product.stock()).isEqualTo(150);
        assertThat(product.active()).isTrue();
    }

    @Test
    void throwsInvalidProductException_whenNameIsBlank() {
        ProductCsvRow row = new ProductCsvRow("SKU-001", null, "desc", "Electronics", 29.99, 150, true);

        assertThatThrownBy(() -> processor.process(row)).isInstanceOf(InvalidProductException.class);
    }

    @Test
    void throwsInvalidProductException_whenSkuIsBlank() {
        ProductCsvRow row = new ProductCsvRow(null, "Wireless Mouse", "desc", "Electronics", 29.99, 150, true);

        assertThatThrownBy(() -> processor.process(row)).isInstanceOf(InvalidProductException.class);
    }
}
