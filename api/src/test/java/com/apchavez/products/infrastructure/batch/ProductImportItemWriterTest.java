package com.apchavez.products.infrastructure.batch;

import com.apchavez.products.application.ProductApplicationService;
import com.apchavez.products.domain.exception.DuplicateSkuException;
import com.apchavez.products.domain.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductImportItemWriterTest {

    private final ProductApplicationService applicationService = mock(ProductApplicationService.class);
    private final ProductImportItemWriter writer = new ProductImportItemWriter(applicationService);

    @Test
    void callsCreateProductOncePerItem() {
        Product p1 = new Product(null, "SKU-001", "Mouse", null, null, 10.0, 5, true);
        Product p2 = new Product(null, "SKU-002", "Keyboard", null, null, 20.0, 3, true);
        when(applicationService.createProduct(any())).thenReturn(Mono.just(p1), Mono.just(p2));

        writer.write(Chunk.of(p1, p2));

        verify(applicationService, times(1)).createProduct(p1);
        verify(applicationService, times(1)).createProduct(p2);
    }

    @Test
    void propagatesDuplicateSkuException_withoutCatchingIt() {
        Product p1 = new Product(null, "SKU-001", "Mouse", null, null, 10.0, 5, true);
        when(applicationService.createProduct(p1)).thenReturn(Mono.error(new DuplicateSkuException("SKU-001")));

        assertThatThrownBy(() -> writer.write(Chunk.of(p1))).isInstanceOf(DuplicateSkuException.class);
    }
}
