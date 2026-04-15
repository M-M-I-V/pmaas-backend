package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.ImportResponse;
import dev.mmiv.pmaas.dto.ImportRowError;
import dev.mmiv.pmaas.entity.InventoryItem;
import dev.mmiv.pmaas.entity.ItemCategory;
import dev.mmiv.pmaas.repository.InventoryItemRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryImportService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final String ALLOWED_MIME_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final InventoryItemRepository repository;
    private final AuditLogService auditLogService;

    @Transactional
    public ImportResponse importFromExcel(MultipartFile file) {
        validateFile(file);

        String importedBy = SecurityContextHolder.getContext()
            .getAuthentication()
            .getName();
        LocalDateTime importedAt = LocalDateTime.now();

        int totalRows = 0,
            inserts = 0,
            duplicates = 0,
            failed = 0;
        List<ImportRowError> errors = new ArrayList<>();

        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            DataFormatter formatter = new DataFormatter();

            for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
                Sheet sheet = workbook.getSheetAt(si);
                String sheetName = sheet.getSheetName();

                Optional<ItemCategory> categoryOpt = ItemCategory.fromSheetName(
                    sheetName
                );
                if (categoryOpt.isEmpty()) {
                    log.warn("Skipping unrecognized sheet: '{}'", sheetName);
                    continue;
                }
                ItemCategory category = categoryOpt.get();

                for (int ri = 1; ri <= sheet.getLastRowNum(); ri++) {
                    Row row = sheet.getRow(ri);
                    if (isRowEmpty(row)) continue;

                    totalRows++;
                    List<ImportRowError> rowErrors = new ArrayList<>();

                    String itemName = getCellString(formatter, row, 0);
                    String brandName = getCellString(formatter, row, 1);
                    String description = getCellString(formatter, row, 2);
                    String stockStr = getCellString(formatter, row, 3);
                    String expStr = getCellString(formatter, row, 4);
                    String recvStr = getCellString(formatter, row, 5);
                    String remarks = getCellString(formatter, row, 6);

                    if (itemName.isBlank()) {
                        rowErrors.add(
                            new ImportRowError(
                                sheetName,
                                ri + 1,
                                "itemName",
                                itemName,
                                "Item name is required"
                            )
                        );
                    }

                    Integer stockOnHand = parseInteger(stockStr);
                    if (stockOnHand == null) {
                        rowErrors.add(
                            new ImportRowError(
                                sheetName,
                                ri + 1,
                                "stockOnHand",
                                stockStr,
                                "Must be a valid non-negative integer"
                            )
                        );
                    } else if (stockOnHand < 0) {
                        rowErrors.add(
                            new ImportRowError(
                                sheetName,
                                ri + 1,
                                "stockOnHand",
                                stockStr,
                                "Stock on hand cannot be negative"
                            )
                        );
                        stockOnHand = null;
                    }

                    LocalDate expirationDate = parseDate(row, 4, formatter);
                    if (!expStr.isBlank() && expirationDate == null) {
                        rowErrors.add(
                            new ImportRowError(
                                sheetName,
                                ri + 1,
                                "expirationDate",
                                expStr,
                                "Invalid date format — expected YYYY-MM-DD or Excel date"
                            )
                        );
                    }

                    LocalDate dateReceived = parseDate(row, 5, formatter);
                    if (!recvStr.isBlank() && dateReceived == null) {
                        rowErrors.add(
                            new ImportRowError(
                                sheetName,
                                ri + 1,
                                "dateReceived",
                                recvStr,
                                "Invalid date format — expected YYYY-MM-DD or Excel date"
                            )
                        );
                    }

                    if (!rowErrors.isEmpty()) {
                        errors.addAll(rowErrors);
                        failed++;
                        continue;
                    }

                    if (
                        repository.existsByItemNameIgnoreCaseAndDateReceivedAndCategory(
                            itemName,
                            dateReceived,
                            category
                        )
                    ) {
                        duplicates++;
                        continue;
                    }

                    InventoryItem item = InventoryItem.builder()
                        .itemName(itemName.trim())
                        .brandName(
                            brandName.isBlank() ? null : brandName.trim()
                        )
                        .category(category)
                        .description(
                            description.isBlank() ? null : description.trim()
                        )
                        .stockOnHand(stockOnHand)
                        .expirationDate(expirationDate)
                        .dateReceived(dateReceived)
                        .remarks(remarks.isBlank() ? null : remarks.trim())
                        .build();

                    repository.save(item);
                    inserts++;
                }
            }
        } catch (IOException e) {
            log.error(
                "Failed to read Excel file '{}': {}",
                file.getOriginalFilename(),
                e.getMessage()
            );
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Could not read the uploaded Excel file."
            );
        }

        auditLogService.record(
            "Inventory",
            0,
            "IMPORT",
            String.format(
                "File: %s | Rows: %d | Inserted: %d | Duplicates: %d | Failed: %d",
                file.getOriginalFilename(),
                totalRows,
                inserts,
                duplicates,
                failed
            )
        );

        log.info(
            "[IMPORT] user='{}' file='{}' rows={} inserts={} dupes={} failed={}",
            importedBy,
            file.getOriginalFilename(),
            totalRows,
            inserts,
            duplicates,
            failed
        );

        return new ImportResponse(
            file.getOriginalFilename(),
            importedBy,
            importedAt,
            totalRows,
            inserts,
            duplicates,
            failed,
            errors
        );
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "File must not be empty."
            );
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(
                HttpStatus.CONTENT_TOO_LARGE,
                "File size exceeds the 5 MB limit."
            );
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPE.equals(contentType)) {
            throw new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Only .xlsx files are accepted (application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)."
            );
        }
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int ci = 0; ci < 7; ci++) {
            Cell cell = row.getCell(ci);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private String getCellString(DataFormatter formatter, Row row, int col) {
        Cell cell = row.getCell(
            col,
            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
        );
        if (cell == null) return "";
        return formatter.formatCellValue(cell).trim();
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            double d = Double.parseDouble(value.replace(",", ""));
            if (d < 0 || d != Math.floor(d)) return null;
            return (int) d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(Row row, int col, DataFormatter formatter) {
        Cell cell = row.getCell(
            col,
            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
        );
        if (cell == null) return null;

        if (
            cell.getCellType() == CellType.NUMERIC &&
            DateUtil.isCellDateFormatted(cell)
        ) {
            try {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            } catch (Exception e) {
                return null;
            }
        }

        String raw = formatter.formatCellValue(cell).trim();
        if (raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
