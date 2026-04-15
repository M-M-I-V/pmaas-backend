package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.ImportResponse;
import dev.mmiv.pmaas.dto.ImportRowError;
import dev.mmiv.pmaas.entity.Contact;
import dev.mmiv.pmaas.entity.ModeOfCommunication;
import dev.mmiv.pmaas.entity.Respond;
import dev.mmiv.pmaas.entity.VisitType;
import dev.mmiv.pmaas.repository.ContactRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads the "Contacts Log 2025" worksheet from an uploaded .xlsx file,
 * validates each data row, skips duplicates, and batch-inserts valid rows.
 *
 * Column mapping (0-indexed, matching the existing Excel logbook):
 *
 *   0  Date              → contactDate
 *   1  Time              → contactTime
 *   2  Name              → name
 *   3  Designation       → designation
 *   4  Medical/Dental    → visitType
 *   5  Number            → contactNumber
 *   6  Mode of Comm      → modeOfCommunication
 *   7  Purpose           → purpose
 *   8  Remarks           → remarks
 *   9  Respond           → respond
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactImportService {

    private static final String SHEET_NAME = "Contacts Log 2025";
    private static final int BATCH_SIZE = 50;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    private final ContactRepository contactRepository;

    @Transactional
    public ImportResponse importContacts(MultipartFile file)
        throws IOException {
        validateFile(file);

        String filename =
            file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : "unknown";
        String importedBy = getCurrentUsername();
        LocalDateTime importedAt = LocalDateTime.now();

        List<ImportRowError> errors = new ArrayList<>();
        List<Contact> batch = new ArrayList<>();
        int totalRows = 0,
            imported = 0,
            skipped = 0,
            failed = 0;

        try (
            InputStream in = file.getInputStream();
            Workbook workbook = new XSSFWorkbook(in)
        ) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sheet '" +
                        SHEET_NAME +
                        "' not found in the uploaded file. " +
                        "Please ensure the worksheet tab is named exactly '" +
                        SHEET_NAME +
                        "'."
                );
            }

            // Row 0 is the header — start data from row index 1
            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) {
                return new ImportResponse(
                    filename,
                    importedBy,
                    importedAt,
                    0,
                    0,
                    0,
                    0,
                    List.of()
                );
            }

            for (int rowIndex = 1; rowIndex <= lastRowNum; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isRowBlank(row)) continue;

                totalRows++;
                int displayRow = rowIndex + 1; // Human-readable (1-indexed + header)

                try {
                    List<ImportRowError> rowErrors = new ArrayList<>();
                    Contact contact = parseRow(row, displayRow, rowErrors);
                    if (contact == null) {
                        errors.addAll(rowErrors);
                        failed++;
                        continue;
                    }

                    // Duplicate detection
                    if (
                        contactRepository.existsDuplicate(
                            contact.getContactDate(),
                            contact.getContactTime(),
                            contact.getName(),
                            contact.getContactNumber()
                        )
                    ) {
                        log.debug(
                            "Row {}: Duplicate skipped — {}, {}",
                            displayRow,
                            contact.getName(),
                            contact.getContactDate()
                        );
                        skipped++;
                        continue;
                    }

                    batch.add(contact);

                    // Flush in batches to limit memory usage
                    if (batch.size() >= BATCH_SIZE) {
                        contactRepository.saveAll(batch);
                        imported += batch.size();
                        batch.clear();
                    }
                } catch (Exception e) {
                    log.warn(
                        "Row {}: Unexpected error — {}",
                        displayRow,
                        e.getMessage()
                    );
                    errors.add(
                        new ImportRowError(
                            SHEET_NAME,
                            displayRow,
                            "general",
                            "",
                            e.getMessage()
                        )
                    );
                    failed++;
                }
            }

            // Flush remaining
            if (!batch.isEmpty()) {
                contactRepository.saveAll(batch);
                imported += batch.size();
                batch.clear();
            }
        }

        log.info(
            "Import complete — total={}, imported={}, skipped={}, failed={}",
            totalRows,
            imported,
            skipped,
            failed
        );

        return new ImportResponse(
            filename,
            importedBy,
            importedAt,
            totalRows,
            imported,
            skipped,
            failed,
            errors
        );
    }

    private String getCurrentUsername() {
        Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "system";
    }

    // Row parsing

    /**
     * Parses a single data row. Returns null and appends to errors if
     * the row has a validation failure; otherwise returns a Contact.
     */
    private Contact parseRow(
        Row row,
        int displayRow,
        List<ImportRowError> errors
    ) {
        // Required: date
        LocalDate contactDate = parseDateCell(row, 0);
        if (contactDate == null) {
            errors.add(
                new ImportRowError(
                    SHEET_NAME,
                    displayRow,
                    "contactDate",
                    "",
                    "Invalid or missing date in column A."
                )
            );
            return null;
        }

        // Required: name
        String name = parseStringCell(row, 2);
        if (name == null || name.isBlank()) {
            errors.add(
                new ImportRowError(
                    SHEET_NAME,
                    displayRow,
                    "name",
                    "",
                    "Missing name in column C."
                )
            );
            return null;
        }

        LocalTime contactTime = parseTimeCell(row, 1);
        String designation = parseStringCell(row, 3);
        VisitType visitType = parseVisitType(row, 4, displayRow, errors);
        String contactNumber = parseStringCell(row, 5);
        ModeOfCommunication mode = parseModeOfComm(row, 6, displayRow, errors);
        String purpose = parseStringCell(row, 7);
        String remarks = parseStringCell(row, 8);
        Respond respond = parseRespond(row, 9, displayRow, errors);

        return Contact.builder()
            .contactDate(contactDate)
            .contactTime(contactTime)
            .name(name.trim())
            .designation(designation)
            .visitType(visitType)
            .contactNumber(contactNumber)
            .modeOfCommunication(mode)
            .purpose(purpose)
            .remarks(remarks)
            .respond(respond)
            .build();
    }

    // Cell-level parsers

    private LocalDate parseDateCell(Row row, int colIndex) {
        Cell cell = row.getCell(
            colIndex,
            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
        );
        if (cell == null) return null;

        try {
            if (
                cell.getCellType() == CellType.NUMERIC &&
                DateUtil.isCellDateFormatted(cell)
            ) {
                Date date = cell.getDateCellValue();
                return date
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            }
            if (cell.getCellType() == CellType.STRING) {
                String raw = cell.getStringCellValue().trim();
                return LocalDate.parse(raw); // Expects ISO-8601 (yyyy-MM-dd)
            }
        } catch (Exception ignored) {}
        return null;
    }

    private LocalTime parseTimeCell(Row row, int colIndex) {
        Cell cell = row.getCell(
            colIndex,
            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
        );
        if (cell == null) return null;

        try {
            if (
                cell.getCellType() == CellType.NUMERIC &&
                DateUtil.isCellDateFormatted(cell)
            ) {
                // Excel stores time as a fraction of a day
                double fraction = cell.getNumericCellValue();
                long totalSeconds = Math.round(fraction * 86400);
                return LocalTime.ofSecondOfDay(totalSeconds % 86400);
            }
            if (cell.getCellType() == CellType.STRING) {
                String raw = cell.getStringCellValue().trim();
                return LocalTime.parse(raw);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String parseStringCell(Row row, int colIndex) {
        Cell cell = row.getCell(
            colIndex,
            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
        );
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    private VisitType parseVisitType(
        Row row,
        int colIndex,
        int displayRow,
        List<ImportRowError> errors
    ) {
        String raw = parseStringCell(row, colIndex);
        if (raw == null || raw.isBlank()) return null;
        try {
            return VisitType.valueOf(raw.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            errors.add(
                new ImportRowError(
                    SHEET_NAME,
                    displayRow,
                    "visitType",
                    raw,
                    "Unknown visit type. Expected MEDICAL or DENTAL."
                )
            );
            return null;
        }
    }

    private ModeOfCommunication parseModeOfComm(
        Row row,
        int colIndex,
        int displayRow,
        List<ImportRowError> errors
    ) {
        String raw = parseStringCell(row, colIndex);
        if (raw == null || raw.isBlank()) return null;
        try {
            return ModeOfCommunication.fromValue(raw);
        } catch (IllegalArgumentException e) {
            errors.add(
                new ImportRowError(
                    SHEET_NAME,
                    displayRow,
                    "modeOfCommunication",
                    raw,
                    e.getMessage()
                )
            );
            return null;
        }
    }

    private Respond parseRespond(
        Row row,
        int colIndex,
        int displayRow,
        List<ImportRowError> errors
    ) {
        String raw = parseStringCell(row, colIndex);
        if (raw == null || raw.isBlank()) return null;
        try {
            return Respond.fromValue(raw);
        } catch (IllegalArgumentException e) {
            errors.add(
                new ImportRowError(
                    SHEET_NAME,
                    displayRow,
                    "respond",
                    raw,
                    e.getMessage()
                )
            );
            return null;
        }
    }

    // Guards

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "File must not be empty."
            );
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                HttpStatus.CONTENT_TOO_LARGE,
                "File exceeds maximum allowed size of 10 MB."
            );
        }
        String ct = file.getContentType();
        if (
            ct == null ||
            (!ct.contains("spreadsheetml") && !ct.contains("excel"))
        ) {
            throw new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Only .xlsx Excel files are accepted."
            );
        }
    }

    private boolean isRowBlank(Row row) {
        if (row == null) return true;
        for (Cell cell : row) {
            if (
                cell != null &&
                cell.getCellType() != CellType.BLANK &&
                !cell.toString().isBlank()
            ) {
                return false;
            }
        }
        return true;
    }
}
