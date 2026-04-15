package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.entity.InventoryItem;
import dev.mmiv.pmaas.entity.ItemCategory;
import dev.mmiv.pmaas.repository.InventoryItemRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryExportService {

    private static final String[] HEADERS = {
        "ID",
        "Item Name",
        "Brand Name",
        "Category",
        "Description",
        "Stock on Hand",
        "Expiration Date",
        "Date Received",
        "Remarks",
        "Created At",
        "Updated At",
    };

    private static final int STREAMING_WINDOW = 100;

    private final InventoryItemRepository repository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public void exportToExcel(
        String q,
        ItemCategory categoryFilter,
        HttpServletResponse response
    ) {
        String exportedBy = SecurityContextHolder.getContext()
            .getAuthentication()
            .getName();

        String filename =
            "inventory_export_" +
            LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            ) +
            ".xlsx";

        response.setContentType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
        response.setHeader(
            "Content-Disposition",
            "attachment; filename=\"" + filename + "\""
        );

        String normalizedQuery = (q == null || q.isBlank()) ? null : q.trim();

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(STREAMING_WINDOW)) {
            workbook.setCompressTempFiles(true);

            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle dateStyle = buildDateStyle(workbook);

            int totalExported = 0;

            for (ItemCategory category : ItemCategory.values()) {
                if (
                    categoryFilter != null && categoryFilter != category
                ) continue;

                List<InventoryItem> items = fetchItems(
                    normalizedQuery,
                    category
                );
                Sheet sheet = workbook.createSheet(category.name());

                writeHeaderRow(sheet, headerStyle);

                int rowIdx = 1;
                for (InventoryItem item : items) {
                    writeDataRow(sheet, rowIdx++, item, dateStyle);
                    totalExported++;
                }

                for (int i = 0; i < HEADERS.length; i++) {
                    sheet.setColumnWidth(i, 5000);
                }
            }

            workbook.write(response.getOutputStream());
            response.flushBuffer();

            auditLogService.record(
                "Inventory",
                0,
                "EXPORT",
                String.format(
                    "user=%s | filter-query='%s' | filter-category=%s | exported=%d",
                    exportedBy,
                    q,
                    categoryFilter,
                    totalExported
                )
            );

            log.info(
                "[EXPORT] user='{}' query='{}' category='{}' exported={}",
                exportedBy,
                q,
                categoryFilter,
                totalExported
            );
        } catch (IOException e) {
            log.error(
                "Export failed for user '{}': {}",
                exportedBy,
                e.getMessage()
            );
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An error occurred while generating the export file."
            );
        }
    }

    private List<InventoryItem> fetchItems(
        String query,
        ItemCategory category
    ) {
        if (query == null) {
            return repository.findAllByCategoryOrderByItemNameAsc(category);
        }
        Pageable all = PageRequest.of(
            0,
            Integer.MAX_VALUE,
            Sort.by(Sort.Direction.ASC, "itemName")
        );
        return repository.search(query, category, all).getContent();
    }

    private void writeHeaderRow(Sheet sheet, CellStyle style) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeDataRow(
        Sheet sheet,
        int rowIdx,
        InventoryItem item,
        CellStyle dateStyle
    ) {
        Row row = sheet.createRow(rowIdx);
        int col = 0;

        row
            .createCell(col++)
            .setCellValue(item.getId() != null ? item.getId() : 0);
        row.createCell(col++).setCellValue(nvl(item.getItemName()));
        row.createCell(col++).setCellValue(nvl(item.getBrandName()));
        row.createCell(col++).setCellValue(item.getCategory().name());
        row.createCell(col++).setCellValue(nvl(item.getDescription()));
        row
            .createCell(col++)
            .setCellValue(
                item.getStockOnHand() != null ? item.getStockOnHand() : 0
            );

        setDateCell(row.createCell(col++), item.getExpirationDate(), dateStyle);
        setDateCell(row.createCell(col++), item.getDateReceived(), dateStyle);

        row.createCell(col++).setCellValue(nvl(item.getRemarks()));
        row
            .createCell(col++)
            .setCellValue(
                item.getCreatedAt() != null
                    ? item.getCreatedAt().toString()
                    : ""
            );
        row
            .createCell(col)
            .setCellValue(
                item.getUpdatedAt() != null
                    ? item.getUpdatedAt().toString()
                    : ""
            );
    }

    private void setDateCell(
        Cell cell,
        java.time.LocalDate date,
        CellStyle style
    ) {
        if (date != null) {
            cell.setCellValue(date.toString());
        } else {
            cell.setCellValue("");
        }
        cell.setCellStyle(style);
    }

    private CellStyle buildHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle buildDateStyle(Workbook workbook) {
        return workbook.createCellStyle();
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}
