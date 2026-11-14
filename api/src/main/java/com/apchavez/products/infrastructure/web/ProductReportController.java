package com.apchavez.products.infrastructure.web;

import com.apchavez.products.application.ProductApplicationService;
import com.apchavez.products.domain.model.Product;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/products/report")
@Tag(name = "Product Reports", description = "Reportes descargables del catálogo de productos")
public class ProductReportController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final String[] HEADERS = {"SKU", "Nombre", "Categoría", "Precio", "Stock", "Activo"};
    private static final float[] COLUMN_WIDTHS = {80f, 150f, 90f, 60f, 50f, 50f};

    private final ProductApplicationService applicationService;

    public ProductReportController(ProductApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/pdf")
    @Operation(summary = "Descargar reporte PDF de productos",
            description = "Genera un PDF con todos los productos (SKU, nombre, categoría, precio, stock, activo) y un resumen de totales.")
    public Mono<ResponseEntity<byte[]>> downloadPdfReport() {
        return applicationService.findAllProducts()
                .collectList()
                .flatMap(products -> Mono.fromCallable(() -> generatePdf(products))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"products-report.pdf\"")
                        .body(bytes));
    }

    @GetMapping("/excel")
    @Operation(summary = "Descargar reporte Excel de productos",
            description = "Genera un XLSX con todos los productos (SKU, nombre, categoría, precio, stock, activo) y un resumen de totales.")
    public Mono<ResponseEntity<byte[]>> downloadExcelReport() {
        return applicationService.findAllProducts()
                .collectList()
                .flatMap(products -> Mono.fromCallable(() -> generateExcel(products))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(bytes -> ResponseEntity.ok()
                        .contentType(XLSX_MEDIA_TYPE)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"products-report.xlsx\"")
                        .body(bytes));
    }

    private byte[] generatePdf(List<Product> products) {
        double totalValue = products.stream().mapToDouble(p -> p.price() * p.stock()).sum();
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        try (PDDocument document = new PDDocument()) {
            PdfCursor cursor = new PdfCursor(document, regular, bold);
            cursor.startPage();
            cursor.writeTitle("Reporte de Productos");
            cursor.writeHeaderRow(HEADERS, COLUMN_WIDTHS);
            for (Product product : products) {
                cursor.ensureSpaceForRow();
                cursor.writeRow(new String[]{
                        product.sku(),
                        product.name(),
                        product.category() == null ? "" : product.category(),
                        String.format(Locale.US, "%.2f", product.price()),
                        String.valueOf(product.stock()),
                        Boolean.TRUE.equals(product.active()) ? "Sí" : "No"
                }, COLUMN_WIDTHS);
            }
            cursor.writeSummary(products.size(), totalValue);
            cursor.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] generateExcel(List<Product> products) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            SXSSFSheet sheet = workbook.createSheet("Products");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            double totalValue = 0d;
            for (Product product : products) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(product.sku());
                row.createCell(1).setCellValue(product.name());
                row.createCell(2).setCellValue(product.category() == null ? "" : product.category());
                row.createCell(3).setCellValue(product.price());
                row.createCell(4).setCellValue(product.stock());
                row.createCell(5).setCellValue(Boolean.TRUE.equals(product.active()));
                totalValue += product.price() * product.stock();
            }

            Row totalCountRow = sheet.createRow(rowIndex++);
            totalCountRow.createCell(0).setCellValue("Total de productos");
            totalCountRow.createCell(1).setCellValue(products.size());

            Row totalValueRow = sheet.createRow(rowIndex);
            totalValueRow.createCell(0).setCellValue("Valor total de inventario");
            totalValueRow.createCell(1).setCellValue(totalValue);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Minimal helper to lay out a simple tabular PDF report across multiple pages. */
    private static final class PdfCursor {
        private static final float MARGIN = 50f;
        private static final float ROW_HEIGHT = 16f;

        private final PDDocument document;
        private final PDType1Font regular;
        private final PDType1Font bold;
        private PDPageContentStream stream;
        private float cursorY;

        PdfCursor(PDDocument document, PDType1Font regular, PDType1Font bold) {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
        }

        void startPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            cursorY = PDRectangle.LETTER.getHeight() - MARGIN;
        }

        void writeTitle(String title) throws IOException {
            stream.beginText();
            stream.setFont(bold, 16);
            stream.newLineAtOffset(MARGIN, cursorY);
            stream.showText(title);
            stream.endText();
            cursorY -= ROW_HEIGHT * 2;
        }

        void writeHeaderRow(String[] headers, float[] widths) throws IOException {
            writeCells(headers, widths, bold);
            cursorY -= ROW_HEIGHT;
        }

        void writeRow(String[] values, float[] widths) throws IOException {
            writeCells(values, widths, regular);
            cursorY -= ROW_HEIGHT;
        }

        void ensureSpaceForRow() throws IOException {
            if (cursorY < MARGIN + ROW_HEIGHT * 3) {
                stream.close();
                startPage();
            }
        }

        void writeSummary(int totalCount, double totalValue) throws IOException {
            ensureSpaceForRow();
            cursorY -= ROW_HEIGHT;
            stream.beginText();
            stream.setFont(bold, 11);
            stream.newLineAtOffset(MARGIN, cursorY);
            stream.showText(String.format(Locale.US, "Total de productos: %d   |   Valor total de inventario: %.2f",
                    totalCount, totalValue));
            stream.endText();
        }

        private void writeCells(String[] values, float[] widths, PDType1Font font) throws IOException {
            float x = MARGIN;
            for (int i = 0; i < values.length; i++) {
                stream.beginText();
                stream.setFont(font, 9);
                stream.newLineAtOffset(x, cursorY);
                stream.showText(truncate(values[i] == null ? "" : values[i], font));
                stream.endText();
                x += widths[i];
            }
        }

        private String truncate(String value, PDType1Font font) {
            return value.length() > 30 ? value.substring(0, 27) + "..." : value;
        }

        void close() throws IOException {
            stream.close();
        }
    }
}
