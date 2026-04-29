package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.entity.*;
import dev.mmiv.pmaas.entity.Patients;
import dev.mmiv.pmaas.entity.VisitType;
import dev.mmiv.pmaas.repository.NurseNoteRepository;
import dev.mmiv.pmaas.repository.PatientsRepository;
import dev.mmiv.pmaas.repository.VisitManagementRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * CSV import and export for visits, updated for the V13 schema.
 *
 * V13 CHANGES FROM V8:
 *   All shared clinical fields (history, physicalExamFindings, diagnosis, plan,
 *   treatment, diagnosticTestResult, diagnosticTestImage, hama, referralForm)
 *   are now in the base Visits entity. Import sets them via the inherited
 *   setters; export reads them from the base entity directly.
 *
 *   Dental field mapping:
 *     H_HISTORY     → visits.history       (was dental_visits.dental_notes)
 *     H_TREATMENT   → visits.treatment     (was dental_visits.treatment_provided)
 *     H_TOOTH_STATUS → dental_visits.tooth_status (renamed from tooth_involved)
 *
 *   H_DENTAL_NOTES / H_TREATMENT_PROVIDED / H_TOOTH_INVOLVED columns are
 *   kept in the CSV header for backward compatibility with old exports, but
 *   are not used during import (the canonical columns are used instead).
 *   During export they are omitted — the canonical columns carry the data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitImportExportService {

    private final VisitManagementRepository visitManagementRepository;
    private final NurseNoteRepository nurseNoteRepository;
    private final PatientsRepository patientsRepository;

    // ── Header constants ─────────────────────────────────────────────────────
    // CHANGING A HEADER NAME IS A BREAKING CHANGE for stored CSV files.

    private static final String H_PATIENT_ID = "Patient ID";
    private static final String H_PATIENT_NAME = "Patient Name";
    private static final String H_VISIT_DATE = "Visit Date";
    private static final String H_VISIT_TYPE = "Visit Type";
    private static final String H_STATUS = "Status";
    private static final String H_CHIEF_COMPLAINT = "Chief Complaint";
    private static final String H_TEMPERATURE = "Temperature";
    private static final String H_BLOOD_PRESSURE = "Blood Pressure";
    private static final String H_PULSE_RATE = "Pulse Rate";
    private static final String H_RESP_RATE = "Respiratory Rate";
    private static final String H_SPO2 = "SPO2";
    // Shared clinical section (base entity after V13)
    private static final String H_HISTORY = "History";
    private static final String H_EXAM_FINDINGS = "Physical Exam Findings";
    private static final String H_DIAGNOSIS = "Diagnosis";
    private static final String H_PLAN = "Plan";
    private static final String H_TREATMENT = "Treatment";
    private static final String H_NURSE_NOTES = "Nurse Notes";
    private static final String H_HAMA = "HAMA";
    private static final String H_REFERRAL = "Referral Form";
    private static final String H_DIAG_RESULT = "Diagnostic Test Result";
    private static final String H_DIAG_IMAGE = "Diagnostic Test Image";
    // Medical-specific
    private static final String H_MEDICAL_CHART = "Medical Chart Image";
    // Dental-specific
    private static final String H_TOOTH_STATUS = "Tooth Status";
    private static final String H_DENTAL_CHART = "Dental Chart Image";

    private static final String IMPORT_BY = "import";
    private static final String NOTE_SEPARATOR = " | ";

    // ══════════════════════════════════════════════════════════════════════════
    // IMPORT
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void importVisits(MultipartFile file) throws IOException {
        validateImportFile(file);

        CSVFormat format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .build();

        try (
            Reader reader = new InputStreamReader(file.getInputStream());
            CSVParser parser = new CSVParser(reader, format)
        ) {
            int rowCount = 0;
            for (CSVRecord rec : parser) {
                importRow(rec);
                rowCount++;
            }
            log.info(
                "Import complete: {} visits imported from {}",
                rowCount,
                file.getOriginalFilename()
            );
        }
    }

    private void importRow(CSVRecord rec) {
        long rowNum = rec.getRecordNumber() + 1;

        Long patientId = parseLongSafe(rec.get(H_PATIENT_ID));
        if (patientId == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Import failed: invalid or missing Patient ID at row " + rowNum
            );
        }

        Patients patient = patientsRepository
            .findById(patientId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Import failed: patient not found with ID " +
                        patientId +
                        " (row " +
                        rowNum +
                        ")"
                )
            );

        String typeRaw = rec.get(H_VISIT_TYPE);
        VisitType visitType;
        try {
            visitType = VisitType.valueOf(typeRaw.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid visit type '" +
                    typeRaw +
                    "' at row " +
                    rowNum +
                    ". Expected MEDICAL or DENTAL."
            );
        }

        if (visitType == VisitType.MEDICAL) {
            importMedicalRow(rec, patient, rowNum);
        } else {
            importDentalRow(rec, patient, rowNum);
        }
    }

    private void importMedicalRow(
        CSVRecord rec,
        Patients patient,
        long rowNum
    ) {
        MedicalVisits visit = new MedicalVisits();
        applyBaseFields(visit, rec, patient);

        // Nurse vitals (base entity)
        visit.setChiefComplaint(blankToNull(rec.get(H_CHIEF_COMPLAINT)));
        visit.setTemperature(blankToNull(rec.get(H_TEMPERATURE)));
        visit.setBloodPressure(blankToNull(rec.get(H_BLOOD_PRESSURE)));
        visit.setPulseRate(blankToNull(rec.get(H_PULSE_RATE)));
        visit.setRespiratoryRate(blankToNull(rec.get(H_RESP_RATE)));
        visit.setSpo2(blankToNull(rec.get(H_SPO2)));

        // Shared clinical section — all on base entity after V13
        visit.setHistory(blankToNull(rec.get(H_HISTORY)));
        visit.setPhysicalExamFindings(blankToNull(rec.get(H_EXAM_FINDINGS)));
        visit.setDiagnosis(blankToNull(rec.get(H_DIAGNOSIS)));
        visit.setPlan(blankToNull(rec.get(H_PLAN)));
        visit.setTreatment(blankToNull(rec.get(H_TREATMENT)));
        visit.setDiagnosticTestResult(blankToNull(rec.get(H_DIAG_RESULT)));
        visit.setDiagnosticTestImage(blankToNull(rec.get(H_DIAG_IMAGE)));
        visit.setHama(blankToNull(rec.get(H_HAMA)));
        visit.setReferralForm(blankToNull(rec.get(H_REFERRAL)));
        // Medical-specific
        visit.setMedicalChartImage(
            blankToNull(getOptionalColumn(rec, H_MEDICAL_CHART))
        );

        MedicalVisits saved = (MedicalVisits) visitManagementRepository.save(
            visit
        );

        // NurseNote: create NurseNote entity from the Nurse Notes column
        String rawNotes = blankToNull(rec.get(H_NURSE_NOTES));
        if (rawNotes != null) {
            NurseNote note = new NurseNote(saved, rawNotes, IMPORT_BY);
            nurseNoteRepository.save(note);
            log.debug(
                "Row {}: created NurseNote from imported CSV nurse notes field",
                rowNum
            );
        }
    }

    private void importDentalRow(CSVRecord rec, Patients patient, long rowNum) {
        DentalVisits visit = new DentalVisits();
        applyBaseFields(visit, rec, patient);

        // Nurse vitals (base entity)
        visit.setChiefComplaint(blankToNull(rec.get(H_CHIEF_COMPLAINT)));
        visit.setTemperature(blankToNull(rec.get(H_TEMPERATURE)));
        visit.setBloodPressure(blankToNull(rec.get(H_BLOOD_PRESSURE)));
        visit.setPulseRate(blankToNull(rec.get(H_PULSE_RATE)));

        // Shared clinical section — all on base entity after V13
        visit.setHistory(blankToNull(rec.get(H_HISTORY)));
        visit.setPhysicalExamFindings(blankToNull(rec.get(H_EXAM_FINDINGS)));
        visit.setDiagnosis(blankToNull(rec.get(H_DIAGNOSIS)));
        visit.setPlan(blankToNull(rec.get(H_PLAN)));
        visit.setTreatment(blankToNull(rec.get(H_TREATMENT)));
        visit.setReferralForm(blankToNull(rec.get(H_REFERRAL)));

        // Dental-specific
        visit.setToothStatus(
            blankToNull(getOptionalColumn(rec, H_TOOTH_STATUS))
        );
        visit.setDentalChartImage(
            blankToNull(getOptionalColumn(rec, H_DENTAL_CHART))
        );

        visitManagementRepository.save(visit);
    }

    /**
     * Applies fields common to both visit types.
     * Imported visits are always COMPLETED so they don't pollute workflow queues.
     */
    private void applyBaseFields(
        Visits visit,
        CSVRecord rec,
        Patients patient
    ) {
        visit.setPatient(patient);
        visit.setVisitDate(parseDateSafe(rec.get(H_VISIT_DATE)));
        visit.setStatus(VisitStatus.COMPLETED);
        visit.setCreatedBy(IMPORT_BY);
        visit.setCompletedAt(LocalDateTime.now());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXPORT
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public void exportVisits(HttpServletResponse response) throws IOException {
        List<Visits> visits = visitManagementRepository.findAllWithPatient();

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(
            "Content-Disposition",
            "attachment; filename=\"visits.csv\""
        );

        // BOM for Excel UTF-8 compatibility
        OutputStream out = response.getOutputStream();
        out.write(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });

        CSVFormat format = CSVFormat.DEFAULT.builder()
            .setHeader(
                H_PATIENT_ID,
                H_PATIENT_NAME,
                H_VISIT_DATE,
                H_VISIT_TYPE,
                H_STATUS,
                H_CHIEF_COMPLAINT,
                H_TEMPERATURE,
                H_BLOOD_PRESSURE,
                H_PULSE_RATE,
                H_RESP_RATE,
                H_SPO2,
                H_HISTORY,
                H_EXAM_FINDINGS,
                H_DIAGNOSIS,
                H_PLAN,
                H_TREATMENT,
                H_NURSE_NOTES,
                H_HAMA,
                H_REFERRAL,
                H_DIAG_RESULT,
                H_DIAG_IMAGE,
                H_MEDICAL_CHART,
                H_TOOTH_STATUS,
                H_DENTAL_CHART
            )
            .build();

        try (
            Writer writer = new OutputStreamWriter(out);
            CSVPrinter csv = new CSVPrinter(writer, format)
        ) {
            for (Visits v : visits) {
                writeVisitRow(csv, v);
            }
            csv.flush();
        }

        log.info("Export complete: {} visits exported", visits.size());
    }

    private void writeVisitRow(CSVPrinter csv, Visits v) throws IOException {
        String patientId =
            v.getPatient() != null
                ? String.valueOf(v.getPatient().getId())
                : "";
        String patientName =
            v.getPatient() != null ? buildFullName(v.getPatient()) : "";

        // Subtype-specific fields
        String medicalChart = null;
        String nurseNotes = null;
        String toothStatus = null;
        String dentalChart = null;

        if (v instanceof MedicalVisits m) {
            medicalChart = m.getMedicalChartImage();
            // Join nurse notes with separator; CSVPrinter handles quoting for commas
            nurseNotes = m.getNurseNotes().isEmpty()
                ? null
                : m
                      .getNurseNotes()
                      .stream()
                      .map(NurseNote::getContent)
                      .reduce((a, b) -> a + NOTE_SEPARATOR + b)
                      .orElse(null);
        } else if (v instanceof DentalVisits d) {
            toothStatus = d.getToothStatus();
            dentalChart = d.getDentalChartImage();
        }

        // All shared clinical fields come from the base entity after V13
        csv.printRecord(
            patientId,
            patientName,
            v.getVisitDate() != null ? v.getVisitDate().toString() : "",
            v.getVisitType() != null ? v.getVisitType().name() : "",
            v.getStatus() != null ? v.getStatus().name() : "",
            v.getChiefComplaint(),
            v.getTemperature(),
            v.getBloodPressure(),
            v.getPulseRate(),
            v.getRespiratoryRate(),
            v.getSpo2(),
            v.getHistory(),
            v.getPhysicalExamFindings(),
            v.getDiagnosis(),
            v.getPlan(),
            v.getTreatment(),
            nurseNotes,
            v.getHama(),
            v.getReferralForm(),
            v.getDiagnosticTestResult(),
            v.getDiagnosticTestImage(),
            medicalChart,
            toothStatus,
            dentalChart
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "File must not be empty."
            );
        }
        String ct = file.getContentType();
        boolean isCsv =
            (ct != null && (ct.contains("csv") || ct.contains("text/plain"))) ||
            (file.getOriginalFilename() != null &&
                file.getOriginalFilename().endsWith(".csv"));
        if (!isCsv) {
            throw new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Only .csv files are accepted for visit import."
            );
        }
    }

    private String buildFullName(Patients p) {
        if (p == null) return "";
        String mi = (p.getMiddleInitial() != null &&
            !p.getMiddleInitial().isBlank())
            ? " " + p.getMiddleInitial() + "."
            : "";
        return p.getFirstName() + mi + " " + p.getLastName();
    }

    private LocalDate parseDateSafe(String dateStr) {
        try {
            return (dateStr == null || dateStr.isBlank())
                ? null
                : LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Long parseLongSafe(String val) {
        try {
            return (val == null || val.isBlank())
                ? null
                : Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private String getOptionalColumn(CSVRecord rec, String header) {
        try {
            return rec.get(header);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
