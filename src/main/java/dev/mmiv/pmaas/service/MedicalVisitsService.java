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

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class MedicalVisitsService {

    private final MedicalVisitsRepository medicalVisitsRepository;
    private final PatientsRepository patientsRepository;
    private final AuditLogService auditLogService;

    public MedicalVisitsService(MedicalVisitsRepository medicalVisitsRepository,
                                PatientsRepository patientsRepository,
                                AuditLogService auditLogService) {
        this.medicalVisitsRepository = medicalVisitsRepository;
        this.patientsRepository = patientsRepository;
        this.auditLogService = auditLogService;
    }

    public List<MedicalVisits> getMedicalVisits() {
        return medicalVisitsRepository.findAll();
    }

    public MedicalVisits getMedicalVisitById(int id) {
        return medicalVisitsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medical visit not found"));
    }

    public MedicalVisitResponse getMedicalVisitResponseById(int id) {
        MedicalVisits visit = getMedicalVisitById(id);
        Patients p = visit.getPatient();

        return new MedicalVisitResponse(
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
                visit.getHama(),
                visit.getReferralForm(),
                visit.getMedicalChartImage(),
                visit.getDiagnosticTestResult(),
                visit.getDiagnosticTestImage(),
                visit.getNursesNotes(),
                p.getFirstName() + " " + p.getLastName(),
                p.getBirthDate()
        );
    }

    public void createMedicalVisits(MultipartFile chartFile,
                                    MultipartFile diagnosticFile,
                                    MedicalVisitRequest dto) throws IOException {
        MedicalVisits medicalVisits = new MedicalVisits();
        saveOrUpdateMedicalVisit(medicalVisits, chartFile, diagnosticFile, dto);
    }

    public void updateMedicalVisits(int id,
                                    MultipartFile chartFile,
                                    MultipartFile diagnosticFile,
                                    MedicalVisitRequest dto) throws IOException {
        MedicalVisits medicalVisits = medicalVisitsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medical visit not found"));
        saveOrUpdateMedicalVisit(medicalVisits, chartFile, diagnosticFile, dto);
        auditLogService.record("MedicalVisits", id, "UPDATE", "Updated visit details");
    }

    public void deleteMedicalVisits(int id) {
        if (medicalVisitsRepository.existsById(id)) {
            medicalVisitsRepository.deleteById(id);
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medical visit not found");
        }
    }

    private void saveOrUpdateMedicalVisit(MedicalVisits medicalVisits,
                                          MultipartFile chartFile,
                                          MultipartFile diagnosticFile,
                                          MedicalVisitRequest dto) throws IOException {
        try {
            Patients patient = patientsRepository.findById(dto.getPatientId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

            medicalVisits.setVisitDate(LocalDate.parse(dto.getVisitDate()));
            medicalVisits.setVisitType(VisitType.valueOf(dto.getVisitType().toUpperCase()));
            medicalVisits.setChiefComplaint(dto.getChiefComplaint());
            medicalVisits.setTemperature(parseDouble(dto.getTemperature()));
            medicalVisits.setBloodPressure(dto.getBloodPressure());
            medicalVisits.setPulseRate(parseInt(dto.getPulseRate()));
            medicalVisits.setRespiratoryRate(parseInt(dto.getRespiratoryRate()));
            medicalVisits.setSpo2(parseDouble(dto.getSpo2()));
            medicalVisits.setHistory(dto.getHistory());
            medicalVisits.setPhysicalExamFindings(dto.getPhysicalExamFindings());
            medicalVisits.setDiagnosis(dto.getDiagnosis());
            medicalVisits.setPlan(dto.getPlan());
            medicalVisits.setTreatment(dto.getTreatment());
            medicalVisits.setHama(dto.getHama());
            medicalVisits.setReferralForm(dto.getReferralForm());
            medicalVisits.setDiagnosticTestResult(dto.getDiagnosticTestResult());
            medicalVisits.setNursesNotes(dto.getNursesNotes());
            medicalVisits.setPatient(patient);

            String chartImage = saveUploadedFile(chartFile);
            if (chartImage != null) medicalVisits.setMedicalChartImage(chartImage);

            String diagnosticImage = saveUploadedFile(diagnosticFile);
            if (diagnosticImage != null) medicalVisits.setDiagnosticTestImage(diagnosticImage);

            medicalVisitsRepository.save(medicalVisits);

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

            String originalName = multipartFile.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
                originalName = originalName.substring(0, originalName.lastIndexOf("."));
            }

            String uniqueFileName = originalName + "_" + System.currentTimeMillis() + extension;
            String uploadPath = uploadDir + "/" + uniqueFileName;

            multipartFile.transferTo(new File(uploadPath));
            return "http://localhost:8080/uploads/" + uniqueFileName;
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