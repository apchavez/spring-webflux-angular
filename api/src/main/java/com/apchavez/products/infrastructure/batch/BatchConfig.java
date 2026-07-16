package com.apchavez.products.infrastructure.batch;

import com.apchavez.products.application.ProductApplicationService;
import com.apchavez.products.domain.model.Product;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;

/**
 * Deliberately does NOT use {@code @EnableBatchProcessing} — that would take over Spring Batch's
 * configuration and disable Spring Boot's autoconfiguration of {@link JobRepository}/{@code JobExplorer}
 * and the {@code spring.batch.jdbc.initialize-schema} property. This class only supplies the
 * job/step/reader/writer beans; Boot's {@code BatchAutoConfiguration} supplies the rest ({@code
 * JobRepository} et al.), resolving its own blocking connection the same way Flyway does — directly
 * from {@link JdbcConnectionDetails}/{@code spring.datasource.*}, not from a general {@code
 * javax.sql.DataSource} bean (Boot's {@code DataSourceAutoConfiguration} backs off in this app since
 * a reactive {@code ConnectionFactory} bean always exists — see {@link #batchDataSource} below).
 */
@Configuration
public class BatchConfig {

    public static final String JOB_NAME = "productImportJob";

    /**
     * Spring Boot's {@code DataSourceAutoConfiguration} deliberately backs off in this app
     * (it's {@code @ConditionalOnMissingBean(ConnectionFactory.class)}, and a reactive R2DBC
     * {@code ConnectionFactory} bean always exists here) — so no general {@code javax.sql.DataSource}
     * bean is ever published, even though Flyway and Spring Batch's own {@code JobRepository} each
     * build their own private blocking connection internally (from the {@link JdbcConnectionDetails}
     * bean Testcontainers' {@code @ServiceConnection} publishes in tests, or from
     * {@code spring.datasource.*} properties in dev/prod). {@code batchTransactionManager} needs an
     * actual {@link DataSource} bean to wrap, so this mirrors that same
     * connection-details-first-then-properties resolution explicitly, rather than assuming a bean
     * that Boot never publishes.
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSourceProperties batchDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource batchDataSource(DataSourceProperties batchDataSourceProperties,
                                       ObjectProvider<JdbcConnectionDetails> connectionDetailsProvider) {
        JdbcConnectionDetails connectionDetails = connectionDetailsProvider.getIfAvailable();
        if (connectionDetails != null) {
            return DataSourceBuilder.create()
                    .url(connectionDetails.getJdbcUrl())
                    .username(connectionDetails.getUsername())
                    .password(connectionDetails.getPassword())
                    .driverClassName(connectionDetails.getDriverClassName())
                    .build();
        }
        return batchDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean
    public ThreadPoolTaskExecutor batchImportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setThreadNamePrefix("batch-import-");
        executor.initialize();
        return executor;
    }

    /**
     * Overrides Boot's default (synchronous) job launching so {@code jobOperator.start(...)} returns
     * immediately while the step executes on {@link #batchImportTaskExecutor()} — never on a Reactor
     * Netty event-loop thread. {@link JobOperator} (via {@link TaskExecutorJobOperator}) is Spring
     * Batch 6's non-deprecated replacement for the old {@code JobLauncher}/{@code TaskExecutorJobLauncher}.
     * Named {@code productImportJobOperator} (not {@code jobOperator}) because Boot's own
     * {@code BatchAutoConfiguration} unconditionally registers a bean literally named "jobOperator" —
     * reusing that name throws a {@code BeanDefinitionOverrideException} instead of the old
     * {@code @ConditionalOnMissingBean} back-off behavior {@code JobLauncher} used to have.
     * {@code @Primary} makes this the one injected wherever {@link JobOperator} is autowired by type.
     * {@code setJobRegistry} is required by {@code afterPropertiesSet()} ("JobLocator must be
     * provided") even though this app never looks a job up by name — {@code start(Job, JobParameters)}
     * is always called with the {@link Job} bean directly, so an empty registry is sufficient.
     */
    @Bean
    @Primary
    public JobOperator productImportJobOperator(JobRepository jobRepository, ThreadPoolTaskExecutor batchImportTaskExecutor) throws Exception {
        TaskExecutorJobOperator operator = new TaskExecutorJobOperator();
        operator.setJobRepository(jobRepository);
        operator.setTaskExecutor(batchImportTaskExecutor);
        operator.setJobRegistry(new MapJobRegistry());
        operator.afterPropertiesSet();
        return operator;
    }

    @Bean
    @StepScope
    public FlatFileItemReader<ProductCsvRow> productCsvReader(@Value("#{jobParameters['filePath']}") String filePath) {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames("sku", "name", "description", "category", "price", "stock", "active");
        tokenizer.setStrict(true);

        return new FlatFileItemReaderBuilder<ProductCsvRow>()
                .name("productCsvReader")
                .resource(new FileSystemResource(filePath))
                .linesToSkip(1)
                .lineTokenizer(tokenizer)
                .fieldSetMapper(new ProductCsvFieldSetMapper())
                .build();
    }

    @Bean
    public ProductImportItemProcessor productImportItemProcessor() {
        return new ProductImportItemProcessor();
    }

    @Bean
    public ProductImportItemWriter productImportItemWriter(ProductApplicationService applicationService) {
        return new ProductImportItemWriter(applicationService);
    }

    @Bean
    public ProductImportSkipListener productImportSkipListener() {
        return new ProductImportSkipListener();
    }

    @Bean
    public Step productImportStep(JobRepository jobRepository,
                                   DataSource batchDataSource,
                                   FlatFileItemReader<ProductCsvRow> productCsvReader,
                                   ProductImportItemProcessor productImportItemProcessor,
                                   ProductImportItemWriter productImportItemWriter,
                                   ProductImportSkipListener productImportSkipListener) {
        // Chunk size is deliberately 1, not a larger batch size. Each createProduct(...) call in the
        // writer commits via its own independent R2DBC transaction as soon as its Mono completes — it
        // does NOT participate in Spring Batch's JDBC chunk transaction. With a chunk size > 1, a write
        // failure on one item (e.g. a duplicate SKU) triggers Spring Batch's fault-tolerant chunk-scan
        // recovery, which RE-INVOKES the writer for every other item already in that chunk to isolate
        // the failure — but those items' R2DBC writes already committed independently on the first
        // attempt, so re-invoking createProduct(...) for them throws a second, spurious
        // DuplicateSkuException and wrongly reports an already-successfully-imported row as skipped.
        // Chunk size 1 sidesteps this entirely: a write failure directly identifies the one failing
        // item, with no other items to rescan.
        //
        // The JdbcTransactionManager below is built inline, NOT as its own @Bean — a Spring-managed
        // PlatformTransactionManager bean here would sit alongside the app's existing (reactive)
        // TransactionManager bean and break ProductApplicationService's plain @Transactional methods:
        // Spring's ambient TransactionManager resolution for an unqualified @Transactional looks up
        // by the shared TransactionManager marker interface and throws NoUniqueBeanDefinitionException
        // once 2 candidates exist. Keeping this one un-registered avoids that collision entirely.
        return new StepBuilder("productImportStep", jobRepository)
                .<ProductCsvRow, Product>chunk(1)
                .transactionManager(new JdbcTransactionManager(batchDataSource))
                .reader(productCsvReader)
                .processor(productImportItemProcessor)
                .writer(productImportItemWriter)
                .faultTolerant()
                // A custom SkipPolicy, not .skip(Class...)/.skipLimit(...) — see ProductImportSkipPolicy's
                // Javadoc: Spring Batch 6 wraps every write in a retry loop by default (even with no
                // .retry(...) registered), and once that's exhausted the exception reaching skip
                // evaluation is a wrapping RetryException, which the plain exception-list shortcut never
                // matches against the original DuplicateSkuException/InvalidProductException types.
                .skipPolicy(new ProductImportSkipPolicy())
                .listener(productImportSkipListener)
                .skipListener(productImportSkipListener)
                .build();
    }

    @Bean
    public ProductImportJobExecutionListener productImportJobExecutionListener() {
        return new ProductImportJobExecutionListener();
    }

    @Bean
    public Job productImportJob(JobRepository jobRepository, Step productImportStep,
                                 ProductImportJobExecutionListener productImportJobExecutionListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(productImportStep)
                .listener(productImportJobExecutionListener)
                .build();
    }
}
