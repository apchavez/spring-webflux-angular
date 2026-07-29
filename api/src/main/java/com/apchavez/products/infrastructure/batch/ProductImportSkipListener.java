package com.apchavez.products.infrastructure.batch;

import com.apchavez.products.domain.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records per-row skip failures keyed by job execution ID, so {@code ProductImportController}'s
 * status endpoint can read them back later. Deliberately an in-memory {@link ConcurrentHashMap}
 * rather than Spring Batch's {@code StepExecution.ExecutionContext} — that context is designed
 * around step-restart persistence semantics (when exactly it gets flushed to the JobRepository
 * mid-step, especially around skip/rollback handling, proved unreliable in practice for this
 * use case), and this data doesn't need to survive an app restart: it's operational feedback for
 * a one-off admin-triggered import, not durable job state. Entries are never evicted — acceptable
 * for a portfolio demo's import volume, not meant for high-churn production use.
 */
public class ProductImportSkipListener implements SkipListener<ProductCsvRow, Product>, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ProductImportSkipListener.class);
    private static final Map<Long, List<RowError>> ERRORS_BY_JOB_EXECUTION_ID = new ConcurrentHashMap<>();

    private StepExecution stepExecution;

    // Explicit StepExecutionListener implementation rather than an @BeforeStep-annotated method —
    // Spring Batch 6's builder-based listener auto-detection reliably recognizes implemented listener
    // interfaces; annotation-based detection did not fire during the write-failure "scan" recovery
    // path in testing, leaving this.stepExecution null when onSkipInWrite needed it.
    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        int lineNumber = (t instanceof FlatFileParseException parseException) ? parseException.getLineNumber() : -1;
        record(lineNumber, null, t.getMessage());
    }

    @Override
    public void onSkipInProcess(ProductCsvRow item, Throwable t) {
        record(-1, item.sku(), t.getMessage());
    }

    @Override
    public void onSkipInWrite(Product item, Throwable t) {
        record(-1, item.sku(), t.getMessage());
    }

    private void record(int lineNumber, String sku, String message) {
        log.warn("Fila de importación omitida — línea={}, sku={}, motivo={}", lineNumber, sku, message);
        ERRORS_BY_JOB_EXECUTION_ID
                .computeIfAbsent(stepExecution.getJobExecutionId(), id -> new CopyOnWriteArrayList<>())
                .add(new RowError(lineNumber, sku, message));
    }

    public static List<RowError> readErrors(long jobExecutionId) {
        return ERRORS_BY_JOB_EXECUTION_ID.getOrDefault(jobExecutionId, List.of());
    }
}
