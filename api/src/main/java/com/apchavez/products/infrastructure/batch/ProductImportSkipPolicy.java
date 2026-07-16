package com.apchavez.products.infrastructure.batch;

import com.apchavez.products.domain.exception.DuplicateSkuException;
import com.apchavez.products.domain.exception.InvalidProductException;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.core.retry.RetryException;

/**
 * Custom {@link SkipPolicy} instead of the builder's {@code .skip(Class...)}/{@code .skipLimit(...)}
 * shortcut. Spring Batch 6's {@code ChunkOrientedStep} wraps every write in a retry loop by default
 * (even with no {@code .retry(...)} registered); once that retry is exhausted, the exception that
 * reaches skip evaluation is a {@link RetryException} wrapping the real cause, not the original
 * {@link DuplicateSkuException}/{@link InvalidProductException} — so a plain {@code .skip(Class...)}
 * list (which only matches the original types) never matches and the step fails instead of skipping.
 * This policy unwraps {@link RetryException} first.
 */
public class ProductImportSkipPolicy implements SkipPolicy {

    static final long SKIP_LIMIT = 100_000;

    @Override
    public boolean shouldSkip(Throwable t, long skipCount) throws SkipLimitExceededException {
        if (skipCount >= SKIP_LIMIT) {
            return false;
        }
        Throwable actual = unwrap(t);
        return actual instanceof FlatFileParseException
                || actual instanceof InvalidProductException
                || actual instanceof DuplicateSkuException;
    }

    private static Throwable unwrap(Throwable t) {
        // RetryException.getLastException() delegates to getCause(), which the class
        // guarantees non-null (it wraps super.getCause() in Objects.requireNonNull) —
        // no null-check needed here, and one would be dead code per RetryException's own contract.
        return t instanceof RetryException retryException ? retryException.getLastException() : t;
    }
}
