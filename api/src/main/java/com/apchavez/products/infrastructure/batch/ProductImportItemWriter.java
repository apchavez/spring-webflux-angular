package com.apchavez.products.infrastructure.batch;

import com.apchavez.products.application.ProductApplicationService;
import com.apchavez.products.domain.model.Product;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/**
 * Delegates each row to {@link ProductApplicationService#createProduct(Product)} unmodified, so
 * duplicate-SKU detection, domain invariant validation, and Kafka event publishing all fire per
 * imported row exactly like the single-create endpoint. This runs on the dedicated batch task
 * executor (see {@code BatchConfig}), never on a Reactor Netty event-loop thread, so blocking here
 * is safe. Deliberately does NOT catch domain exceptions — they propagate so Spring Batch's
 * fault-tolerant skip policy can isolate and skip the offending row via its chunk-scan-on-write-failure
 * mechanism.
 */
public class ProductImportItemWriter implements ItemWriter<Product> {

    private final ProductApplicationService applicationService;

    public ProductImportItemWriter(ProductApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public void write(Chunk<? extends Product> chunk) {
        for (Product product : chunk) {
            applicationService.createProduct(product).block();
        }
    }
}
