package com.apchavez.products.infrastructure.batch;

import com.apchavez.products.domain.model.Product;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * Constructing {@link Product} triggers its compact constructor's invariant checks
 * (throws {@link com.apchavez.products.domain.exception.InvalidProductException}) — the same
 * validation the single-create endpoint gets for free. That exception is registered as skippable
 * in {@code BatchConfig}, so an invalid row is skipped without failing the whole import.
 */
public class ProductImportItemProcessor implements ItemProcessor<ProductCsvRow, Product> {

    @Override
    public Product process(ProductCsvRow row) {
        return new Product(
                null,
                row.sku(),
                row.name(),
                row.description(),
                row.category(),
                row.price(),
                row.stock(),
                row.active());
    }
}
