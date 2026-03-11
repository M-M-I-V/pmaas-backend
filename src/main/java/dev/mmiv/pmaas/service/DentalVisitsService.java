package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.DentalVisitRequest;
import dev.mmiv.pmaas.dto.DentalVisitResponse;
import dev.mmiv.pmaas.entity.DentalVisits;
import dev.mmiv.pmaas.entity.Patients;
import dev.mmiv.pmaas.entity.VisitType;
import dev.mmiv.pmaas.repository.DentalVisitsRepository;
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
 * S-06 FIX: File saving delegated to FileStorageService (UUID filenames, extension whitelist).
 * S-07 FIX: Upload URL no longer hardcoded; FileStorageService reads APP_BASE_URL env var.
 * S-18 FIX: Duplicate saveUploadedFile() method removed; FileStorageService is the single impl.
 * Audit: CREATE, UPDATE, DELETE, and READ (sensitive access) events are all now recorded.
 */
@Service
public class DentalVisitsService {

    private final DentalVisitsRepository dentalVisitsRepository;
    private final PatientsRepository patientsRepository;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;

    public DentalVisitsService(DentalVisitsRepository dentalVisitsRepository,
                               PatientsRepository patientsRepository,
                               AuditLogService auditLogService,
                               FileStorageService fileStorageService) {
        this.dentalVisitsRepository = dentalVisitsRepository;
        this.patientsRepository     = patientsRepository;
        this.auditLogService        = auditLogService;
        this.fileStorageService     = fileStorageService;
    }

    public List<DentalVisits> getDentalVisits() {
        return dentalVisitsRepository.findAll();
    }

    public DentalVisits getDentalVisitById(int id) {
        return dentalVisitsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Dental visit not found."));
    }

    public DentalVisitResponse getDentalVisitResponseById(int id) {
        DentalVisits visit = getDentalVisitById(id);
        Patients p = visit.getPatient();

        auditLogService.record("DentalVisits", id, "READ", "Dental visit record accessed.");

        return new DentalVisitResponse(
                visit.getId(), visit.getVisitDate(), visit.getVisitType().name(),
                visit.getChiefComplaint(), visit.getTemperature(), visit.getBloodPressure(),
                visit.getPulseRate(), visit.getRespiratoryRate(), visit.getSpo2(),
                visit.getHistory(), visit.getPhysicalExamFindings(), visit.getDiagnosis(),
                visit.getPlan(), visit.getTreatment(), visit.getDentalChartImage(),
                visit.getToothStatus(), visit.getDiagnosticTestResult(),
                visit.getDiagnosticTestImage(),
                p.getFirstName() + " " + p.getLastName(), p.getBirthDate()
        );
    }

    public void createDentalVisits(MultipartFile chartFile,
                                   MultipartFile diagnosticFile,
                                   DentalVisitRequest dto) throws IOException {
        DentalVisits visit = new DentalVisits();
        saveOrUpdateDentalVisit(visit, chartFile, diagnosticFile, dto);
        auditLogService.record("DentalVisits", visit.getId(), "CREATE",
                "New dental visit created for patient ID " + dto.getPatientId());
    }

    public void updateDentalVisits(int id, MultipartFile chartFile,
                                   MultipartFile diagnosticFile,
                                   DentalVisitRequest dto) throws IOException {
        DentalVisits visit = dentalVisitsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Dental visit not found."));
        saveOrUpdateDentalVisit(visit, chartFile, diagnosticFile, dto);
        auditLogService.record("DentalVisits", id, "UPDATE", "Updated visit details.");
    }

    public void deleteDentalVisits(int id) {
        if (!dentalVisitsRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dental visit not found.");
        }
        dentalVisitsRepository.deleteById(id);
        auditLogService.record("DentalVisits", id, "DELETE", "Dental visit deleted.");
    }

    private void saveOrUpdateDentalVisit(DentalVisits visit,
                                         MultipartFile chartFile,
                                         MultipartFile diagnosticFile,
                                         DentalVisitRequest dto) throws IOException {
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
            visit.setDiagnosticTestResult(dto.getDiagnosticTestResult());
            visit.setToothStatus(dto.getToothStatus());
            visit.setPatient(patient);

            // Only overwrite stored paths if a new file was actually uploaded
            String chartUrl = fileStorageService.save(chartFile);
            if (chartUrl != null) visit.setDentalChartImage(chartUrl);

            String diagnosticUrl = fileStorageService.save(diagnosticFile);
            if (diagnosticUrl != null) visit.setDiagnosticTestImage(diagnosticUrl);

            dentalVisitsRepository.save(visit);

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