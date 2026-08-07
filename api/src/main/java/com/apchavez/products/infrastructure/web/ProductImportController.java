package com.apchavez.products.infrastructure.web;

import com.apchavez.products.infrastructure.batch.ProductImportSkipListener;
import com.apchavez.products.infrastructure.web.dto.ImportJobResponseDTO;
import com.apchavez.products.infrastructure.web.dto.ImportJobStatusResponseDTO;
import com.apchavez.products.infrastructure.web.dto.ImportRowErrorDTO;
import com.apchavez.products.infrastructure.web.exception.InvalidImportFileException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/products/import")
@Tag(name = "Product Import", description = "Importación masiva de productos vía CSV")
public class ProductImportController {

    private static final Logger log = LoggerFactory.getLogger(ProductImportController.class);

    private final JobOperator jobOperator;
    private final Job productImportJob;
    private final JobRepository jobRepository;

    public ProductImportController(JobOperator jobOperator, Job productImportJob, JobRepository jobRepository) {
        this.jobOperator = jobOperator;
        this.productImportJob = productImportJob;
        this.jobRepository = jobRepository;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importar productos desde CSV",
            description = "Encola una importación masiva asíncrona. Filas inválidas o con SKU duplicado se omiten individualmente; consultar el resultado con GET /api/v1/products/import/{jobExecutionId}.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Importación encolada",
                    content = @Content(schema = @Schema(implementation = ImportJobResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "El archivo no es un CSV válido")
    })
    public Mono<ResponseEntity<ImportJobResponseDTO>> importProducts(@RequestPart("file") Mono<FilePart> filePartMono) {
        return filePartMono
                .flatMap(this::validateAndStore)
                .flatMap(this::launchImportJob)
                .map(jobExecution -> ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(new ImportJobResponseDTO(jobExecution.getId(), jobExecution.getStatus().toString())));
    }

    @GetMapping("/{jobExecutionId}")
    @Operation(summary = "Consultar estado de una importación",
            description = "Retorna el estado y resumen (filas leídas/escritas/omitidas + errores) de una ejecución de importación.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado encontrado",
                    content = @Content(schema = @Schema(implementation = ImportJobStatusResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "No existe una ejecución con ese ID")
    })
    public Mono<ResponseEntity<ImportJobStatusResponseDTO>> getImportStatus(@PathVariable Long jobExecutionId) {
        return Mono.fromCallable(() -> buildStatusResponse(jobExecutionId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(dto -> ResponseEntity.ok(dto))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    private Mono<StoredUpload> validateAndStore(FilePart filePart) {
        String filename = filePart.filename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            return Mono.error(new InvalidImportFileException("El archivo debe tener extensión .csv"));
        }
        Path tempFile;
        try {
            // java.io.tmpdir is a shared, publicly-writable directory on most deployment
            // targets — restrict the created file to owner-only access (rather than
            // inheriting the directory's default, world-readable permissions) to avoid
            // exposing uploaded product data to other local users/processes.
            tempFile = Files.createTempFile("product-import-", ".csv", ownerOnlyFileAttribute());
            tempFile.toFile().deleteOnExit();
        } catch (IOException e) {
            return Mono.error(new UncheckedIOException(e));
        }
        return DataBufferUtils.write(filePart.content(), tempFile, StandardOpenOption.TRUNCATE_EXISTING)
                .then(Mono.just(new StoredUpload(tempFile, filename)));
    }

    private Mono<JobExecution> launchImportJob(StoredUpload upload) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .defaultIfEmpty("unknown")
                .flatMap(submittedBy -> Mono.fromCallable(() -> {
                    JobParameters jobParameters = new JobParametersBuilder()
                            .addString("filePath", upload.tempFile().toAbsolutePath().toString())
                            .addString("originalFilename", upload.originalFilename())
                            .addString("submittedBy", submittedBy)
                            .addLong("timestamp", System.currentTimeMillis())
                            .toJobParameters();
                    log.info("Encolando importación de productos — archivo='{}', usuario='{}'",
                            upload.originalFilename(), submittedBy);
                    return jobOperator.start(productImportJob, jobParameters);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    private ImportJobStatusResponseDTO buildStatusResponse(Long jobExecutionId) {
        JobExecution jobExecution = jobRepository.getJobExecution(jobExecutionId);
        if (jobExecution == null) {
            return null;
        }

        long readCount = 0;
        long writeCount = 0;
        long skipCount = 0;
        List<ImportRowErrorDTO> errors = List.of();

        Optional<StepExecution> stepExecutionRef = jobExecution.getStepExecutions().stream().findFirst();
        if (stepExecutionRef.isPresent()) {
            StepExecution stepExecution = jobRepository.getStepExecution(stepExecutionRef.get().getId());
            if (stepExecution != null) {
                readCount = stepExecution.getReadCount();
                writeCount = stepExecution.getWriteCount();
                skipCount = stepExecution.getSkipCount();
                errors = ProductImportSkipListener.readErrors(jobExecutionId).stream()
                        .map(e -> new ImportRowErrorDTO(e.lineNumber(), e.sku(), e.message()))
                        .toList();
            }
        }

        return new ImportJobStatusResponseDTO(
                jobExecutionId, jobExecution.getStatus().toString(), readCount, writeCount, skipCount, errors);
    }

    private static FileAttribute<?>[] ownerOnlyFileAttribute() {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))};
        }
        return new FileAttribute<?>[0];
    }

    private record StoredUpload(Path tempFile, String originalFilename) {}
}
