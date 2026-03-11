package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.MedicalVisitRequest;
import dev.mmiv.pmaas.dto.MedicalVisitResponse;
import dev.mmiv.pmaas.entity.MedicalVisits;
import dev.mmiv.pmaas.entity.Patients;
import dev.mmiv.pmaas.entity.VisitType;
import dev.mmiv.pmaas.repository.MedicalVisitsRepository;
import dev.mmiv.pmaas.repository.PatientsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * File saving delegated to FileStorageService (UUID filenames, extension whitelist).
 * Upload URL no longer hardcoded; FileStorageService reads APP_BASE_URL env var.
 * Duplicate saveUploadedFile() method removed; FileStorageService is the single impl.
 * Audit: CREATE, UPDATE, DELETE, and READ (sensitive access) events are all now recorded.
 */
@Service
public class MedicalVisitsService {

    private final MedicalVisitsRepository medicalVisitsRepository;
    private final PatientsRepository patientsRepository;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;

    public MedicalVisitsService(MedicalVisitsRepository medicalVisitsRepository,
                                PatientsRepository patientsRepository,
                                AuditLogService auditLogService,
                                FileStorageService fileStorageService) {
        this.medicalVisitsRepository = medicalVisitsRepository;
        this.patientsRepository      = patientsRepository;
        this.auditLogService         = auditLogService;
        this.fileStorageService      = fileStorageService;
    }

    public List<MedicalVisits> getMedicalVisits() {
        return medicalVisitsRepository.findAll();
    }

    public MedicalVisits getMedicalVisitById(int id) {
        return medicalVisitsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Medical visit not found."));
    }

    public MedicalVisitResponse getMedicalVisitResponseById(int id) {
        MedicalVisits visit = getMedicalVisitById(id);
        Patients p = visit.getPatient();

        auditLogService.record("MedicalVisits", id, "READ", "Medical visit record accessed.");

        return new MedicalVisitResponse(
                visit.getId(), visit.getVisitDate(), visit.getVisitType().name(),
                visit.getChiefComplaint(), visit.getTemperature(), visit.getBloodPressure(),
                visit.getPulseRate(), visit.getRespiratoryRate(), visit.getSpo2(),
                visit.getHistory(), visit.getPhysicalExamFindings(), visit.getDiagnosis(),
                visit.getPlan(), visit.getTreatment(), visit.getHama(), visit.getReferralForm(),
                visit.getMedicalChartImage(), visit.getDiagnosticTestResult(),
                visit.getDiagnosticTestImage(), visit.getNursesNotes(),
                p.getFirstName() + " " + p.getLastName(), p.getBirthDate()
        );
    }

    public void createMedicalVisits(MultipartFile chartFile,
                                    MultipartFile diagnosticFile,
                                    MedicalVisitRequest dto) throws IOException {
        MedicalVisits visit = new MedicalVisits();
        saveOrUpdateMedicalVisit(visit, chartFile, diagnosticFile, dto);
        auditLogService.record("MedicalVisits", visit.getId(), "CREATE",
                "New medical visit created for patient ID " + dto.getPatientId());
    }

    public void updateMedicalVisits(int id, MultipartFile chartFile,
                                    MultipartFile diagnosticFile,
                                    MedicalVisitRequest dto) throws IOException {
        MedicalVisits visit = medicalVisitsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Medical visit not found."));
        saveOrUpdateMedicalVisit(visit, chartFile, diagnosticFile, dto);
        auditLogService.record("MedicalVisits", id, "UPDATE", "Updated visit details.");
    }

    public void deleteMedicalVisits(int id) {
        if (!medicalVisitsRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medical visit not found.");
        }
        medicalVisitsRepository.deleteById(id);
        auditLogService.record("MedicalVisits", id, "DELETE", "Medical visit deleted.");
    }

    private void saveOrUpdateMedicalVisit(MedicalVisits visit,
                                          MultipartFile chartFile,
                                          MultipartFile diagnosticFile,
                                          MedicalVisitRequest dto) throws IOException {
        try {
            Patients patient = patientsRepository.findById(dto.getPatientId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Patient not found."));

            visit.setVisitDate(LocalDate.parse(dto.getVisitDate()));
            visit.setVisitType(VisitType.valueOf(dto.getVisitType().toUpperCase()));
            visit.setChiefComplaint(dto.getChiefComplaint());
            visit.setTemperature(parseDouble(dto.getTemperature()));
            visit.setBloodPressure(dto.getBloodPressure());
            visit.setPulseRate(parseInt(dto.getPulseRate()));
            visit.setRespiratoryRate(parseInt(dto.getRespiratoryRate()));
            visit.setSpo2(parseDouble(dto.getSpo2()));
            visit.setHistory(dto.getHistory());
            visit.setPhysicalExamFindings(dto.getPhysicalExamFindings());
            visit.setDiagnosis(dto.getDiagnosis());
            visit.setPlan(dto.getPlan());
            visit.setTreatment(dto.getTreatment());
            visit.setHama(dto.getHama());
            visit.setReferralForm(dto.getReferralForm());
            visit.setDiagnosticTestResult(dto.getDiagnosticTestResult());
            visit.setNursesNotes(dto.getNursesNotes());
            visit.setPatient(patient);

            // Only overwrite stored paths if a new file was actually uploaded
            String chartUrl = fileStorageService.save(chartFile);
            if (chartUrl != null) visit.setMedicalChartImage(chartUrl);

            String diagnosticUrl = fileStorageService.save(diagnosticFile);
            if (diagnosticUrl != null) visit.setDiagnosticTestImage(diagnosticUrl);

            medicalVisitsRepository.save(visit);

        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid date format. Use yyyy-MM-dd.");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid visit type: " + dto.getVisitType());
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid numeric value: " + value);
        }
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid integer value: " + value);
        }
    }
}