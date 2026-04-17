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
 * CSV import and export for visits, updated for the V8 workflow schema.
 *
 * KEY CHANGES FROM OLD VisitsService:
 *
 * 1. nursesNotes TEXT column is GONE.
 *    - Import: if the "Nurse Notes" CSV column has content, a NurseNote entity
 *      is created and persisted. Historical imported visits are set to COMPLETED
 *      so the workflow machinery does not interfere with them.
 *    - Export: nurse notes are fetched from the NurseNote relationship and
 *      joined with " | " as a separator so the round-trip is lossless.
 *
 * 2. Vitals are now String, not Double/int.
 *    - temperature, spo2: were Double → now String (no parsing, just store raw)
 *    - pulseRate, respiratoryRate: were int → now String
 *    - This is backward-compatible: old CSVs with "72" in the Pulse Rate column
 *      still import correctly as the string "72".
 *
 * 3. visitType is a JPA discriminator (insertable=false, updatable=false).
 *    - We do NOT call visit.setVisitType() — Hibernate sets the discriminator
 *      column automatically from @DiscriminatorValue.
 *
 * 4. Export uses Apache Commons CSVPrinter instead of writer.printf.
 *    - The old printf approach was broken for medical data fields containing
 *      commas (e.g., "Hypertension, Stage 1" produced extra columns).
 *    - CSVPrinter quotes any value containing commas, newlines, or quotes,
 *      and the import-side CSVParser handles them correctly.
 *
 * 5. Imported visits get status=COMPLETED and createdBy="import" so they
 *    don't appear as open workflow items in the nurse/MD queues.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitImportExportService {

    private final VisitManagementRepository visitManagementRepository;
    private final NurseNoteRepository nurseNoteRepository;
    private final PatientsRepository patientsRepository;

    // Header constants — MUST stay in sync between import and export
    // Changing a header name here is a breaking change for any stored CSV files.
    private static final String H_PATIENT_ID = "Patient ID";
    private static final String H_VISIT_DATE = "Visit Date";
    private static final String H_VISIT_TYPE = "Visit Type";
    private static final String H_CHIEF_COMPLAINT = "Chief Complaint";
    private static final String H_DIAGNOSIS = "Diagnosis";
    private static final String H_TEMPERATURE = "Temperature";
    private static final String H_BLOOD_PRESSURE = "Blood Pressure";
    private static final String H_PULSE_RATE = "Pulse Rate";
    private static final String H_RESP_RATE = "Respiratory Rate";
    private static final String H_SPO2 = "SPO2";
    private static final String H_HISTORY = "History";
    private static final String H_EXAM_FINDINGS = "Physical Exam Findings";
    private static final String H_PLAN = "Plan";
    private static final String H_TREATMENT = "Treatment";
    private static final String H_NURSE_NOTES = "Nurse Notes";
    private static final String H_HAMA = "HAMA";
    private static final String H_REFERRAL = "Referral Form";
    private static final String H_DIAG_RESULT = "Diagnostic Test Result";
    private static final String H_DIAG_IMAGE = "Diagnostic Test Image";
    // Dental-specific
    private static final String H_DENTAL_NOTES = "Dental Notes";
    private static final String H_TREATMENT_PROVIDED = "Treatment Provided";
    private static final String H_TOOTH_INVOLVED = "Tooth Involved";
    // Read-only in export, not imported
    private static final String H_PATIENT_NAME = "Patient Name";
    private static final String H_STATUS = "Status";

    private static final String IMPORT_BY = "import";
    private static final String NOTE_SEPARATOR = " | ";

    // IMPORT

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
        long rowNum = rec.getRecordNumber() + 1; // +1 for header row offset

        // ── Resolve patient ───────────────────────────────────────────────────
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

        // Determine visit type
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

        // Build the correct subtype
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

        // Nurse-owned vitals
        visit.setChiefComplaint(blankToNull(rec.get(H_CHIEF_COMPLAINT)));
        visit.setTemperature(blankToNull(rec.get(H_TEMPERATURE)));
        visit.setBloodPressure(blankToNull(rec.get(H_BLOOD_PRESSURE)));
        visit.setPulseRate(blankToNull(rec.get(H_PULSE_RATE)));
        visit.setRespiratoryRate(blankToNull(rec.get(H_RESP_RATE)));
        visit.setSpo2(blankToNull(rec.get(H_SPO2)));
        visit.setWeight(blankToNull(rec.get("weight"))); // May not exist in old exports
        visit.setHeight(blankToNull(rec.get("height"))); // May not exist in old exports

        // Medical-specific fields
        visit.setHistory(blankToNull(rec.get(H_HISTORY)));
        visit.setPhysicalExamFindings(blankToNull(rec.get(H_EXAM_FINDINGS)));
        visit.setDiagnosis(blankToNull(rec.get(H_DIAGNOSIS)));
        visit.setPlan(blankToNull(rec.get(H_PLAN)));
        visit.setTreatment(blankToNull(rec.get(H_TREATMENT)));
        visit.setDiagnosticTestResult(blankToNull(rec.get(H_DIAG_RESULT)));
        visit.setDiagnosticTestImage(blankToNull(rec.get(H_DIAG_IMAGE)));
        visit.setHama(blankToNull(rec.get(H_HAMA)));
        visit.setReferralForm(blankToNull(rec.get(H_REFERRAL)));

        MedicalVisits saved = (MedicalVisits) visitManagementRepository.save(
            visit
        );

        // NurseNote: nursesNotes TEXT is gone, create NurseNote entity
        // The old "Nurse Notes" CSV column now creates an immutable NurseNote.
        // Notes from different authors are separated by " | " in the CSV cell —
        // on import they are stored as a single combined note.
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

        // Nurse-owned vitals
        visit.setChiefComplaint(blankToNull(rec.get(H_CHIEF_COMPLAINT)));
        visit.setTemperature(blankToNull(rec.get(H_TEMPERATURE)));
        visit.setBloodPressure(blankToNull(rec.get(H_BLOOD_PRESSURE)));
        visit.setPulseRate(blankToNull(rec.get(H_PULSE_RATE)));

        // DMD-owned fields
        visit.setDiagnosis(blankToNull(rec.get(H_DIAGNOSIS)));
        visit.setDentalNotes(
            blankToNull(getOptionalColumn(rec, H_DENTAL_NOTES))
        );
        visit.setTreatmentProvided(
            blankToNull(getOptionalColumn(rec, H_TREATMENT_PROVIDED))
        );
        visit.setToothInvolved(
            blankToNull(getOptionalColumn(rec, H_TOOTH_INVOLVED))
        );
        visit.setPlan(blankToNull(rec.get(H_PLAN)));
        visit.setReferralForm(blankToNull(rec.get(H_REFERRAL)));

        visitManagementRepository.save(visit);
    }

    /**
     * Applies fields common to both MedicalVisits and DentalVisits.
     *
     * NOTE: do NOT call visit.setVisitType() — it is a discriminator column
     * marked insertable=false, updatable=false. Hibernate sets it automatically
     * from the @DiscriminatorValue on MedicalVisits / DentalVisits.
     *
     * Imported visits are set to COMPLETED so they don't pollute the active
     * workflow queues (nurse assignment list, MD pending list).
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

    // EXPORT

    /**
     * Exports all visits to CSV using Apache Commons CSVPrinter.
     *
     * WHY NOT writer.printf():
     *   The old export used writer.printf with raw commas between fields. Any
     *   medical text field containing a comma (diagnosis: "Hypertension, Stage 1")
     *   would produce an extra column, breaking the CSV structure. CSVPrinter
     *   handles quoting automatically — a value containing a comma is wrapped
     *   in double quotes, and the import-side CSVParser unwraps it correctly.
     *
     * Nurse notes: all notes for a medical visit are joined with " | " into a
     * single CSV cell. On re-import they are stored as one combined note. This
     * preserves the content while keeping the CSV single-row-per-visit format.
     */
    @Transactional(readOnly = true)
    public void exportVisits(HttpServletResponse response) throws IOException {
        List<Visits> visits = visitManagementRepository.findAllWithPatient();

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(
            "Content-Disposition",
            "attachment; filename=\"visits.csv\""
        );

        // Write BOM so Excel opens UTF-8 CSVs with correct encoding automatically
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
                H_DENTAL_NOTES,
                H_TREATMENT_PROVIDED,
                H_TOOTH_INVOLVED
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
        String chiefComplaint = null;
        String temperature = null,
            bloodPressure = null;
        String pulseRate = null,
            respiratoryRate = null,
            spo2 = null;
        String history = null,
            physicalExam = null;
        String diagnosis = null,
            plan = null;
        String treatment = null,
            nurseNotes = null;
        String hama = null,
            referralForm = null;
        String diagResult = null,
            diagImage = null;
        String dentalNotes = null,
            treatmentProvided = null,
            toothInvolved = null;

        if (v instanceof MedicalVisits m) {
            chiefComplaint = m.getChiefComplaint();
            temperature = m.getTemperature();
            bloodPressure = m.getBloodPressure();
            pulseRate = m.getPulseRate();
            respiratoryRate = m.getRespiratoryRate();
            spo2 = m.getSpo2();
            history = m.getHistory();
            physicalExam = m.getPhysicalExamFindings();
            diagnosis = m.getDiagnosis();
            plan = m.getPlan();
            treatment = m.getTreatment();
            hama = m.getHama();
            referralForm = m.getReferralForm();
            diagResult = m.getDiagnosticTestResult();
            diagImage = m.getDiagnosticTestImage();
            // Join all nurse notes with a separator — CSVPrinter handles quoting
            nurseNotes = m.getNurseNotes().isEmpty()
                ? null
                : m
                      .getNurseNotes()
                      .stream()
                      .map(n -> n.getContent())
                      .reduce((a, b) -> a + NOTE_SEPARATOR + b)
                      .orElse(null);
        } else if (v instanceof DentalVisits d) {
            chiefComplaint = d.getChiefComplaint();
            temperature = d.getTemperature();
            bloodPressure = d.getBloodPressure();
            pulseRate = d.getPulseRate();
            diagnosis = d.getDiagnosis();
            plan = d.getPlan();
            referralForm = d.getReferralForm();
            dentalNotes = d.getDentalNotes();
            treatmentProvided = d.getTreatmentProvided();
            toothInvolved = d.getToothInvolved();
        }

        csv.printRecord(
            patientId,
            patientName,
            v.getVisitDate() != null ? v.getVisitDate().toString() : "",
            v.getVisitType() != null ? v.getVisitType().name() : "",
            v.getStatus() != null ? v.getStatus().name() : "",
            chiefComplaint,
            temperature,
            bloodPressure,
            pulseRate,
            respiratoryRate,
            spo2,
            history,
            physicalExam,
            diagnosis,
            plan,
            treatment,
            nurseNotes,
            hama,
            referralForm,
            diagResult,
            diagImage,
            dentalNotes,
            treatmentProvided,
            toothInvolved
        );
    }

    // HELPERS

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

    /**
     * Returns null for blank strings so JPA stores NULL in the database
     * instead of an empty string, which is consistent with how the workflow
     * steps set these fields.
     */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * Reads an optional column — returns empty string if the column
     * header does not exist in the CSV (for backward compatibility
     * with CSVs exported before dental-specific columns were added).
     */
    private String getOptionalColumn(CSVRecord rec, String header) {
        try {
            return rec.get(header);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
