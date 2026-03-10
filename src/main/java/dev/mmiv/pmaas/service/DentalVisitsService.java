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

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class DentalVisitsService {

    private final DentalVisitsRepository dentalVisitsRepository;
    private final PatientsRepository patientsRepository;
    private final AuditLogService auditLogService;

    public DentalVisitsService(DentalVisitsRepository dentalVisitsRepository,
                               PatientsRepository patientsRepository,
                               AuditLogService auditLogService) {
        this.dentalVisitsRepository = dentalVisitsRepository;
        this.patientsRepository = patientsRepository;
        this.auditLogService = auditLogService;
    }

    public List<DentalVisits> getDentalVisits() {
        return dentalVisitsRepository.findAll();
    }

    public DentalVisits getDentalVisitById(int id) {
        return dentalVisitsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dental visit not found"));
    }

    public DentalVisitResponse getDentalVisitResponseById(int id) {
        DentalVisits visit = getDentalVisitById(id);
        Patients p = visit.getPatient();

        return new DentalVisitResponse(
                visit.getId(),
                visit.getVisitDate(),
                visit.getVisitType().name(),
                visit.getChiefComplaint(),
                visit.getTemperature(),
                visit.getBloodPressure(),
                visit.getPulseRate(),
                visit.getRespiratoryRate(),
                visit.getSpo2(),
                visit.getHistory(),
                visit.getPhysicalExamFindings(),
                visit.getDiagnosis(),
                visit.getPlan(),
                visit.getTreatment(),
                visit.getDentalChartImage(),
                visit.getToothStatus(),
                visit.getDiagnosticTestResult(),
                visit.getDiagnosticTestImage(),
                p.getFirstName() + " " + p.getLastName(),
                p.getBirthDate()
        );
    }

    public void createDentalVisits(MultipartFile chartFile,
                                   MultipartFile diagnosticFile,
                                   DentalVisitRequest dto) throws IOException {
        DentalVisits dentalVisits = new DentalVisits();
        saveOrUpdateDentalVisit(dentalVisits, chartFile, diagnosticFile, dto);
    }

    public void updateDentalVisits(int id,
                                   MultipartFile chartFile,
                                   MultipartFile diagnosticFile,
                                   DentalVisitRequest dto) throws IOException {
        DentalVisits dentalVisits = dentalVisitsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dental visit not found"));
        saveOrUpdateDentalVisit(dentalVisits, chartFile, diagnosticFile, dto);
        auditLogService.record("DentalVisits", dentalVisits.getId(), "UPDATE", "Updated visit details");
    }

    public void deleteDentalVisits(int id) {
        if (dentalVisitsRepository.existsById(id)) {
            dentalVisitsRepository.deleteById(id);
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dental visit not found");
        }
    }

    private void saveOrUpdateDentalVisit(DentalVisits dentalVisits,
                                         MultipartFile chartFile,
                                         MultipartFile diagnosticFile,
                                         DentalVisitRequest dto) throws IOException {
        try {
            Patients patient = patientsRepository.findById(dto.getPatientId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

            dentalVisits.setVisitDate(LocalDate.parse(dto.getVisitDate()));
            dentalVisits.setVisitType(VisitType.valueOf(dto.getVisitType().toUpperCase()));
            dentalVisits.setChiefComplaint(dto.getChiefComplaint());
            dentalVisits.setTemperature(parseDouble(dto.getTemperature()));
            dentalVisits.setBloodPressure(dto.getBloodPressure());
            dentalVisits.setPulseRate(parseInt(dto.getPulseRate()));
            dentalVisits.setRespiratoryRate(parseInt(dto.getRespiratoryRate()));
            dentalVisits.setSpo2(parseDouble(dto.getSpo2()));
            dentalVisits.setHistory(dto.getHistory());
            dentalVisits.setPhysicalExamFindings(dto.getPhysicalExamFindings());
            dentalVisits.setDiagnosis(dto.getDiagnosis());
            dentalVisits.setPlan(dto.getPlan());
            dentalVisits.setTreatment(dto.getTreatment());
            dentalVisits.setDiagnosticTestResult(dto.getDiagnosticTestResult());
            dentalVisits.setToothStatus(dto.getToothStatus());
            dentalVisits.setPatient(patient);

            String chartImage = saveUploadedFile(chartFile);
            if (chartImage != null) dentalVisits.setDentalChartImage(chartImage);

            String diagnosticImage = saveUploadedFile(diagnosticFile);
            if (diagnosticImage != null) dentalVisits.setDiagnosticTestImage(diagnosticImage);

            dentalVisitsRepository.save(dentalVisits);

        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Use yyyy-MM-dd", e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid visit type: " + dto.getVisitType(), e);
        }
    }

    private String saveUploadedFile(MultipartFile multipartFile) throws IOException {
        if (multipartFile != null && !multipartFile.isEmpty()) {
            String rootPath = System.getProperty("user.dir");
            String uploadDir = rootPath + "/uploads";
            new File(uploadDir).mkdirs();

            String fileName = multipartFile.getOriginalFilename();
            String uploadPath = uploadDir + "/" + fileName;

            multipartFile.transferTo(new File(uploadPath));
            return "http://localhost:8080/uploads/" + fileName;
        }
        return null;
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid numeric value: " + value);
        }
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid integer value: " + value);
        }
    }
}