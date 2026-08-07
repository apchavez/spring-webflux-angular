package com.apchavez.products.infrastructure.batch;

import com.apchavez.products.AbstractIntegrationTest;
import com.apchavez.products.infrastructure.persistence.ProductEntity;
import com.apchavez.products.infrastructure.persistence.ProductR2dbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@SpringBatchTest
class ProductImportJobIntegrationTest extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private ProductR2dbcRepository r2dbcRepository;

    @BeforeEach
    void setUp() {
        r2dbcRepository.deleteAll().block();
        r2dbcRepository.save(new ProductEntity(
                null, "SKU-DUP-001", "Existing Product", "desc", "Electronics", 5.0, 1, true)).block();
    }

    @Test
    void importsValidRows_andSkipsInvalidOnesIndividually() throws Exception {
        // Copy the checked-in fixture to a throwaway temp file — ProductImportJobExecutionListener
        // deletes whatever file the "filePath" job parameter points at once the job finishes (mirroring
        // real upload cleanup), so passing the fixture's own path directly would delete it from source.
        Path fixture = new File("src/test/resources/import/sample-products.csv").toPath();
        Path tempFile = Files.createTempFile("product-import-test-", ".csv");
        Files.copy(fixture, tempFile, StandardCopyOption.REPLACE_EXISTING);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("filePath", tempFile.toAbsolutePath().toString())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(jobExecution.getStatus().isRunning()).isFalse());

        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

        StepExecution stepExecution = jobExecution.getStepExecutions().iterator().next();
        // sample-products.csv: 5 valid rows + 1 duplicate-SKU row (write-skip) +
        // 1 malformed-price row (read-skip) + 1 blank-name row (process-skip) = 8 data rows.
        assertThat(stepExecution.getReadCount()).isEqualTo(7);
        assertThat(stepExecution.getWriteCount()).isEqualTo(5);
        assertThat(stepExecution.getSkipCount()).isEqualTo(3);

        // Content-based, not hasSize(3): ProductImportSkipListener's row-error map is keyed by the
        // numeric JobExecution ID, which Testcontainers-backed test classes can coincidentally share
        // across their own independent Postgres databases within the same JVM test run (each starts
        // its own auto-increment sequence at 1) — a real single-database production instance never
        // has this collision.
        List<RowError> errors = ProductImportSkipListener.readErrors(jobExecution.getId());
        assertThat(errors)
                .extracting(RowError::sku, RowError::message)
                .contains(
                        tuple("SKU-DUP-001", "Ya existe un producto con el SKU: SKU-DUP-001"),
                        tuple("SKU-IMP-007", "El nombre no puede estar vacío"));
        assertThat(errors).anySatisfy(e -> assertThat(e.message()).contains("Parsing error at line: 8"));

        // 5 imported + 1 pre-seeded = 6; the duplicate/malformed/blank rows never landed.
        assertThat(r2dbcRepository.count().block()).isEqualTo(6L);
    }
}
