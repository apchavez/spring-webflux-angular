package com.apchavez.products.infrastructure.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Deletes the uploaded CSV's temp file once the job reaches a terminal state, regardless of
 * outcome. Reads the path from the JobExecution's own JobParameters rather than a stored field,
 * since {@code productImportJob} is a singleton bean shared across concurrent uploads.
 */
public class ProductImportJobExecutionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ProductImportJobExecutionListener.class);

    @Override
    public void afterJob(JobExecution jobExecution) {
        String filePath = jobExecution.getJobParameters().getString("filePath");
        if (filePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException e) {
            log.warn("No se pudo eliminar el archivo temporal de importación: {}", filePath, e);
        }
    }
}
