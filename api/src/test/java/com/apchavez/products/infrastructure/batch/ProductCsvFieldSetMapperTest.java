package com.apchavez.products.infrastructure.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.file.transform.DefaultFieldSet;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductCsvFieldSetMapperTest {

    private static final String[] NAMES = {"sku", "name", "description", "category", "price", "stock", "active"};

    private final ProductCsvFieldSetMapper mapper = new ProductCsvFieldSetMapper();

    private FieldSet fieldSet(String... values) {
        return new DefaultFieldSet(values, NAMES);
    }

    @Test
    void mapsValidRow() throws Exception {
        ProductCsvRow row = mapper.mapFieldSet(
                fieldSet("SKU-001", "Wireless Mouse", "desc", "Electronics", "29.99", "150", "true"));

        assertThat(row.sku()).isEqualTo("SKU-001");
        assertThat(row.name()).isEqualTo("Wireless Mouse");
        assertThat(row.description()).isEqualTo("desc");
        assertThat(row.category()).isEqualTo("Electronics");
        assertThat(row.price()).isEqualTo(29.99);
        assertThat(row.stock()).isEqualTo(150);
        assertThat(row.active()).isTrue();
    }

    @Test
    void mapsBlankDescriptionAndCategoryToNull() throws Exception {
        ProductCsvRow row = mapper.mapFieldSet(
                fieldSet("SKU-001", "Wireless Mouse", "", "", "29.99", "150", "true"));

        assertThat(row.description()).isNull();
        assertThat(row.category()).isNull();
    }

    @Test
    void acceptsCaseInsensitiveBoolean() throws Exception {
        ProductCsvRow row = mapper.mapFieldSet(
                fieldSet("SKU-001", "Wireless Mouse", "desc", "Electronics", "29.99", "150", "FALSE"));

        assertThat(row.active()).isFalse();
    }

    @Test
    void throwsOnMalformedPrice() {
        assertThatThrownBy(() -> mapper.mapFieldSet(
                fieldSet("SKU-001", "Wireless Mouse", "desc", "Electronics", "not-a-number", "150", "true")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void throwsOnMalformedStock() {
        assertThatThrownBy(() -> mapper.mapFieldSet(
                fieldSet("SKU-001", "Wireless Mouse", "desc", "Electronics", "29.99", "abc", "true")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void throwsOnMalformedBoolean() {
        assertThatThrownBy(() -> mapper.mapFieldSet(
                fieldSet("SKU-001", "Wireless Mouse", "desc", "Electronics", "29.99", "150", "yes")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
