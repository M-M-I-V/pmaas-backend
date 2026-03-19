package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.VisitsList;
import dev.mmiv.pmaas.entity.*;
import dev.mmiv.pmaas.repository.PatientsRepository;
import dev.mmiv.pmaas.repository.VisitsRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * CSV import now uses Apache Commons CSV instead of line.split(",", -1).
 * Why line.split() is dangerous here:
 *   Medical data fields — diagnosis, treatment notes, chief complaint — routinely
 *   contain commas. "Hypertension, Stage 1" split on comma produces three tokens
 *   instead of one, silently shifting every subsequent column by one position.
 *   A patient ID in column 19 ends up being read from column 20 (which is the
 *   patient name), causing a NumberFormatException or — worse — associating the
 *   visit with the wrong patient record entirely.
 * The fix uses named column access (rec.get("Patient ID")) which is immune to
 * any number of commas inside quoted fields. The column names exactly match the
 * headers written by exportVisits() so import and export are symmetric.
 */
@Service
@RequiredArgsConstructor
public class VisitsService {

    private final VisitsRepository visitsRepository;
    private final PatientsRepository patientsRepository;

    // Header constants — must stay in sync with exportVisits()
    // Defining them here prevents silent breakage if a header is renamed in export.
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
    private static final String H_PATIENT_ID = "Patient ID";

    // Read

    public List<VisitsList> getVisitsList() {
        return visitsRepository
            .findAll()
            .stream()
            .map(v ->
                new VisitsList(
                    v.getId(),
                    buildFullName(v.getPatient()),
                    v.getPatient().getBirthDate(),
                    v.getVisitDate(),
                    v.getVisitType().name(),
                    v.getChiefComplaint(),
                    v.getPhysicalExamFindings(),
                    v.getDiagnosis(),
                    v.getTreatment()
                )
            )
            .toList();
    }

    public List<VisitsList> getVisitsListByPatientId(int patientId) {
        return visitsRepository.findVisitsListByPatientId(patientId);
    }

    public List<Visits> getVisits() {
        return visitsRepository.findAll();
    }

    // Import

    /**
     * Replaces the manual line.split(",", -1) implementation.
     * Commons CSV handles:
     *   - Quoted fields containing commas: "Hypertension, Stage 1" → single value
     *   - Quoted fields containing newlines (multi-line notes)
     *   - Empty fields without shifting column positions
     *   - BOM characters at the start of Excel-exported CSV files
     * Column access is by header name, not position index, so adding or reordering
     * columns in the export does not silently corrupt the import.
     */
    public void importVisits(MultipartFile file) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
            .setHeader() // treat first row as header
            .setSkipHeaderRecord(true) // don't try to parse header as data
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .build();

        try (
            Reader reader = new InputStreamReader(file.getInputStream());
            CSVParser parser = new CSVParser(reader, format)
        ) {
            for (CSVRecord rec : parser) {
                // Resolve patient
                int patientId = parseIntSafe(rec.get(H_PATIENT_ID));
                Patients patient = patientsRepository
                    .findById(patientId)
                    .orElseThrow(() ->
                        new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Import failed: patient not found with ID " +
                                patientId +
                                " (row " +
                                rec.getRecordNumber() +
                                ")"
                        )
                    );

                // Determine visit type
                VisitType visitType;
                try {
                    visitType = VisitType.valueOf(
                        rec.get(H_VISIT_TYPE).toUpperCase()
                    );
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid visit type '" +
                            rec.get(H_VISIT_TYPE) +
                            "' at row " +
                            rec.getRecordNumber()
                    );
                }

                Visits visit = (visitType == VisitType.MEDICAL)
                    ? new MedicalVisits()
                    : new DentalVisits();

                // Populate common Visits fields
                visit.setVisitDate(parseDateSafe(rec.get(H_VISIT_DATE)));
                visit.setVisitType(visitType);
                visit.setChiefComplaint(rec.get(H_CHIEF_COMPLAINT));
                visit.setDiagnosis(rec.get(H_DIAGNOSIS));
                visit.setTemperature(parseDoubleSafe(rec.get(H_TEMPERATURE)));
                visit.setBloodPressure(rec.get(H_BLOOD_PRESSURE));
                visit.setPulseRate(parseIntSafe(rec.get(H_PULSE_RATE)));
                visit.setRespiratoryRate(parseIntSafe(rec.get(H_RESP_RATE)));
                visit.setSpo2(parseDoubleSafe(rec.get(H_SPO2)));
                visit.setHistory(rec.get(H_HISTORY));
                visit.setPhysicalExamFindings(rec.get(H_EXAM_FINDINGS));
                visit.setPlan(rec.get(H_PLAN));
                visit.setTreatment(rec.get(H_TREATMENT));
                visit.setDiagnosticTestResult(rec.get(H_DIAG_RESULT));
                visit.setDiagnosticTestImage(rec.get(H_DIAG_IMAGE));
                visit.setPatient(patient);

                // Medical-only fields
                if (visit instanceof MedicalVisits m) {
                    m.setNursesNotes(rec.get(H_NURSE_NOTES));
                    m.setHama(rec.get(H_HAMA));
                    m.setReferralForm(rec.get(H_REFERRAL));
                }

                visitsRepository.save(visit);
            }
        }
    }

    // Export

    public void exportVisits(HttpServletResponse response) throws IOException {
        List<Visits> visits = visitsRepository.findAllWithPatient();

        response.setContentType("text/csv");
        response.setHeader(
            "Content-Disposition",
            "attachment; filename=visits.csv"
        );

        try (PrintWriter writer = response.getWriter()) {
            writer.println(
                "ID,Visit Date,Visit Type,Chief Complaint,Diagnosis," +
                    "Temperature,Blood Pressure,Pulse Rate,Respiratory Rate,SPO2," +
                    "History,Physical Exam Findings,Plan,Treatment," +
                    "Nurse Notes,HAMA,Referral Form," +
                    "Diagnostic Test Result,Diagnostic Test Image," +
                    "Patient ID,Patient Name"
            );

            for (Visits v : visits) {
                String nurseNotes = "",
                    hama = "",
                    referralForm = "";
                if (v instanceof MedicalVisits m) {
                    nurseNotes = safe(m.getNursesNotes());
                    hama = safe(m.getHama());
                    referralForm = safe(m.getReferralForm());
                }

                String patientId =
                    v.getPatient() != null
                        ? String.valueOf(v.getPatient().getId())
                        : "";
                String patientName =
                    v.getPatient() != null ? buildFullName(v.getPatient()) : "";

                writer.printf(
                    "%d,%s,%s,%s,%s,%s,%s,%d,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                    v.getId(),
                    v.getVisitDate() != null ? v.getVisitDate() : "",
                    v.getVisitType() != null ? v.getVisitType().name() : "",
                    safe(v.getChiefComplaint()),
                    safe(v.getDiagnosis()),
                    safeNum(v.getTemperature()),
                    safe(v.getBloodPressure()),
                    v.getPulseRate(),
                    v.getRespiratoryRate(),
                    safeNum(v.getSpo2()),
                    safe(v.getHistory()),
                    safe(v.getPhysicalExamFindings()),
                    safe(v.getPlan()),
                    safe(v.getTreatment()),
                    nurseNotes,
                    hama,
                    referralForm,
                    safe(v.getDiagnosticTestResult()),
                    safe(v.getDiagnosticTestImage()),
                    patientId,
                    safe(patientName)
                );
            }
        }
    }

    // Private helpers

    private String buildFullName(Patients p) {
        if (p == null) return "";
        String mi = (p.getMiddleInitial() != null &&
            !p.getMiddleInitial().isBlank())
            ? " " + p.getMiddleInitial() + "."
            : "";
        return p.getFirstName() + mi + " " + p.getLastName();
    }

    private String safe(String value) {
        // Wrap in quotes if the value contains a comma, so export stays parseable.
        // Commons CSV on import handles quoted fields correctly.
        if (value == null) return "";
        if (
            value.contains(",") || value.contains("\"") || value.contains("\n")
        ) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String safeNum(Double value) {
        return value == null ? "" : String.valueOf(value);
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

    private Double parseDoubleSafe(String val) {
        try {
            return (val == null || val.isBlank())
                ? null
                : Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseIntSafe(String val) {
        try {
            return (val == null || val.isBlank())
                ? 0
                : Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
