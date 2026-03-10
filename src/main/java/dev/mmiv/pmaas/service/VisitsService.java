package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.VisitsList;
import dev.mmiv.pmaas.entity.*;
import dev.mmiv.pmaas.repository.PatientsRepository;
import dev.mmiv.pmaas.repository.VisitsRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VisitsService {

    private final VisitsRepository visitsRepository;
    private final PatientsRepository patientsRepository;

    public List<VisitsList> getVisitsList() {
        return visitsRepository.findAll().stream()
                .map(v -> new VisitsList(
                        v.getId(),
                        buildFullName(v.getPatient()),
                        v.getPatient().getBirthDate(),
                        v.getVisitDate(),
                        v.getVisitType().name(),
                        v.getChiefComplaint(),
                        v.getPhysicalExamFindings(),
                        v.getDiagnosis(),
                        v.getTreatment()
                ))
                .toList();
    }

    private String buildFullName(Patients p) {
        if (p == null) return "";
        String mi = (p.getMiddleInitial() != null && !p.getMiddleInitial().isBlank())
                ? " " + p.getMiddleInitial() + "."
                : "";
        return p.getFirstName() + mi + " " + p.getLastName();
    }

    public List<VisitsList> getVisitsListByPatientId(int patientId) {
        return visitsRepository.findVisitsListByPatientId(patientId);
    }

    public List<Visits> getVisits() {
        return visitsRepository.findAll();
    }

    // ✅ Updated Export with new attributes
    public void exportVisits(HttpServletResponse response) throws IOException {
        List<Visits> visits = visitsRepository.findAllWithPatient();

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=visits.csv");

        try (PrintWriter writer = response.getWriter()) {
            writer.println("ID,Visit Date,Visit Type,Chief Complaint,Diagnosis,Temperature,Blood Pressure,Pulse Rate,Respiratory Rate,SPO2,History,Physical Exam Findings,Plan,Treatment,Nurse Notes,HAMA,Referral Form,Diagnostic Test Result,Diagnostic Test Image,Patient ID,Patient Name");

            for (Visits v : visits) {
                String nurseNotes = "";
                String hama = "";
                String referralForm = "";
                String diagnosticResult = safe(v.getDiagnosticTestResult());
                String diagnosticImage = safe(v.getDiagnosticTestImage());
                String patientId = "";
                String patientName = "";

                if (v instanceof MedicalVisits m) {
                    nurseNotes = safe(m.getNursesNotes());
                    hama = safe(m.getHama());
                    referralForm = safe(m.getReferralForm());
                }

                if (v.getPatient() != null) {
                    patientId = String.valueOf(v.getPatient().getId());
                    patientName = buildFullName(v.getPatient());
                }

                writer.printf("%d,%s,%s,%s,%s,%s,%s,%d,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
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
                        safe(nurseNotes),
                        safe(hama),
                        safe(referralForm),
                        diagnosticResult,
                        diagnosticImage,
                        safe(patientId),
                        safe(patientName)
                );
            }
        }
    }

    public void importVisits(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean skipHeader = true;

            while ((line = reader.readLine()) != null) {
                if (skipHeader) {
                    skipHeader = false;
                    continue;
                }

                String[] data = line.split(",", -1);
                if (data.length < 21) continue;

                int patientId = parseInt(data[19]);
                Patients patient = patientsRepository.findById(patientId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found: " + patientId));

                VisitType visitType = data[2].equalsIgnoreCase("DENTAL") ? VisitType.DENTAL : VisitType.MEDICAL;

                Visits visit = (visitType == VisitType.MEDICAL)
                        ? new MedicalVisits()
                        : new DentalVisits();

                visit.setVisitDate(parseDate(data[1]));
                visit.setVisitType(visitType);
                visit.setChiefComplaint(data[3]);
                visit.setDiagnosis(data[4]);
                visit.setTemperature(parseDouble(data[5]));
                visit.setBloodPressure(data[6]);
                visit.setPulseRate(parseInt(data[7]));
                visit.setRespiratoryRate(parseInt(data[8]));
                visit.setSpo2(parseDouble(data[9]));
                visit.setHistory(data[10]);
                visit.setPhysicalExamFindings(data[11]);
                visit.setPlan(data[12]);
                visit.setTreatment(data[13]);
                visit.setDiagnosticTestResult(data[17]);
                visit.setDiagnosticTestImage(data[18]);
                visit.setPatient(patient);

                if (visit instanceof MedicalVisits m) {
                    m.setNursesNotes(data[14]);
                    m.setHama(data[15]);
                    m.setReferralForm(data[16]);
                }

                visitsRepository.save(visit);
            }
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replace(",", " ");
    }

    private String safeNum(Double value) {
        return value == null ? "" : String.valueOf(value);
    }

    private LocalDate parseDate(String dateStr) {
        try {
            return (dateStr == null || dateStr.isBlank()) ? null : LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Double parseDouble(String val) {
        try {
            return (val == null || val.isBlank()) ? null : Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseInt(String val) {
        try {
            return (val == null || val.isBlank()) ? 0 : Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}