package com.apchavez.products.infrastructure.web;

import java.util.Set;
import com.apchavez.products.AbstractIntegrationTest;
import com.apchavez.products.infrastructure.config.JwtService;
import com.apchavez.products.infrastructure.persistence.ProductEntity;
import com.apchavez.products.infrastructure.persistence.ProductR2dbcRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
class ProductReportControllerIntegrationTest extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProductR2dbcRepository r2dbcRepository;

    @Autowired
    private JwtService jwtService;

    private String userToken;

    @BeforeEach
    void setUp() {
        r2dbcRepository.deleteAll().block();
        userToken = jwtService.generateToken("test-user", Set.of("USER"));
        r2dbcRepository.save(new ProductEntity(null, "SKU-100", "Teclado Mecánico", "desc", "Electronics", 49.90, 20, true)).block();
        r2dbcRepository.save(new ProductEntity(null, "SKU-101", "Monitor 24\"", "desc", "Electronics", 159.00, 5, false)).block();
    }

    @Test
    void downloadPdfReport_shouldReturn200_withPdfContent() {
        byte[] body = webTestClient.get()
                .uri("/api/v1/products/report/pdf")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/pdf")
                .expectHeader().valueEquals("Content-Disposition", "attachment; filename=\"products-report.pdf\"")
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.length).isGreaterThan(100);
        // PDF magic bytes
        assertThat(new String(body, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void downloadPdfReport_shouldReturn401_withoutToken() {
        webTestClient.get()
                .uri("/api/v1/products/report/pdf")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void downloadExcelReport_shouldReturn200_withRowsMatchingSeededProducts() throws IOException {
        byte[] body = webTestClient.get()
                .uri("/api/v1/products/report/excel")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Content-Disposition", "attachment; filename=\"products-report.xlsx\"")
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(body))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("SKU");

            Row firstDataRow = sheet.getRow(1);
            assertThat(firstDataRow.getCell(0).getStringCellValue()).isEqualTo("SKU-100");
            assertThat(firstDataRow.getCell(3).getNumericCellValue()).isEqualTo(49.90);

            Row totalCountRow = sheet.getRow(3);
            assertThat(totalCountRow.getCell(0).getStringCellValue()).isEqualTo("Total de productos");
            assertThat(totalCountRow.getCell(1).getNumericCellValue()).isEqualTo(2);
        }
    }

    @Test
    void downloadExcelReport_shouldReturn401_withoutToken() {
        webTestClient.get()
                .uri("/api/v1/products/report/excel")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
