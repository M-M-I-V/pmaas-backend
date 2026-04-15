package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.entity.Contact;
import dev.mmiv.pmaas.repository.ContactRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exports filtered Contact records to an .xlsx file using SXSSFWorkbook.
 *
 * SXSSFWorkbook writes rows directly to a temp file on disk as they are
 * added, keeping only a configurable window of rows in memory. This
 * prevents OutOfMemoryError on large exports without requiring database-
 * level streaming.
 *
 * The export is paginated internally — records are fetched from the
 * database in pages of 500 and written to the workbook incrementally.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactExportService {

    private static final int      PAGE_SIZE      = 500;
    private static final int      ROW_WINDOW     = 100;  // Rows kept in memory by SXSSF
    private static final String   SHEET_NAME     = "Contacts Log 2025";
    private static final String   FILE_PREFIX    = "contacts_export_";
    private static final String[] HEADERS        = {
            "Date", "Time", "Name", "Designation", "Medical/Dental",
            "Number", "Mode of Comm", "Purpose", "Remarks", "Respond"
    };

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ContactRepository contactRepository;

    @Transactional(readOnly = true)
    public void exportContacts(
            Specification<Contact> spec,
            HttpServletResponse response
    ) throws IOException {

        String filename = FILE_PREFIX + System.currentTimeMillis() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_WINDOW)) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);

            // Header row
            CellStyle headerStyle = buildHeaderStyle(workbook);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 20 * 256);
            }

            // Data rows — paginated fetch
            Sort sort = Sort.by(Sort.Direction.ASC, "contactDate", "contactTime");
            int pageNum = 0;
            int rowNum  = 1;
            int totalExported = 0;

            while (true) {
                Pageable pageable = PageRequest.of(pageNum, PAGE_SIZE, sort);
                List<Contact> page = contactRepository.findAll(spec, pageable).getContent();

                if (page.isEmpty()) break;

                for (Contact contact : page) {
                    writeDataRow(sheet, rowNum++, contact);
                    totalExported++;
                }

                if (page.size() < PAGE_SIZE) break;
                pageNum++;
            }

            workbook.write(response.getOutputStream());
            workbook.dispose(); // Remove temp files created by SXSSF

            log.info("Export complete — {} records written to {}", totalExported, filename);
        }
    }

    // Row writer

    private void writeDataRow(Sheet sheet, int rowNum, Contact contact) {
        Row row = sheet.createRow(rowNum);

        row.createCell(0).setCellValue(
                contact.getContactDate() != null
                        ? contact.getContactDate().format(DATE_FMT) : "");

        row.createCell(1).setCellValue(
                contact.getContactTime() != null
                        ? contact.getContactTime().format(TIME_FMT) : "");

        row.createCell(2).setCellValue(nullSafe(contact.getName()));
        row.createCell(3).setCellValue(nullSafe(contact.getDesignation()));

        row.createCell(4).setCellValue(
                contact.getVisitType() != null
                        ? contact.getVisitType().name() : "");

        row.createCell(5).setCellValue(nullSafe(contact.getContactNumber()));

        row.createCell(6).setCellValue(
                contact.getModeOfCommunication() != null
                        ? contact.getModeOfCommunication().getDisplayLabel() : "");

        row.createCell(7).setCellValue(nullSafe(contact.getPurpose()));
        row.createCell(8).setCellValue(nullSafe(contact.getRemarks()));

        row.createCell(9).setCellValue(
                contact.getRespond() != null
                        ? contact.getRespond().getDisplayLabel() : "");
    }

    // Style helpers

    private CellStyle buildHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}