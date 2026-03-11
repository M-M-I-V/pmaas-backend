package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.PatientDTO;
import dev.mmiv.pmaas.dto.PatientList;
import dev.mmiv.pmaas.entity.Patients;
import dev.mmiv.pmaas.repository.PatientsRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PatientsService {

    private final PatientsRepository patientsRepository;
    private final AuditLogService auditLogService;

    public void createPatient(PatientDTO dto) {
        Patients patient = mapDtoToEntity(dto, new Patients());
        patientsRepository.save(patient);
    }

    public List<PatientList> getPatientsList() {
        return patientsRepository.findAll().stream()
                .map(patient -> new PatientList(
                        patient.getId(),
                        patient.getFirstName(),
                        patient.getLastName(),
                        patient.getStudentNumber(),
                        patient.getGender(),
                        patient.getStatus()
                ))
                .toList();
    }

    public List<Patients> getPatients() {
        return patientsRepository.findAll();
    }

    public Patients getPatientById(int id) {
        return patientsRepository.findById(id).orElse(null);
    }

    public void updatePatient(int id, PatientDTO dto) {
        Optional<Patients> existing = patientsRepository.findById(id);
        if (existing.isPresent()) {
            Patients updated = mapDtoToEntity(dto, existing.get());
            patientsRepository.save(updated);
            auditLogService.record("Patients", id, "UPDATE", "Updated patient details");
        } else {
            throw new RuntimeException("Patient with ID " + id + " not found");
        }
    }

    public void deletePatient(int id) {
        patientsRepository.deleteById(id);
    }

    private Patients mapDtoToEntity(PatientDTO dto, Patients patient) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        patient.setStudentNumber(dto.getStudentNumber());
        patient.setLastName(dto.getLastName());
        patient.setFirstName(dto.getFirstName());
        patient.setMiddleInitial(dto.getMiddleInitial());
        patient.setStatus(dto.getStatus());
        patient.setGender(dto.getGender());

        if (dto.getBirthDate() != null && !dto.getBirthDate().isEmpty()) {
            try {
                patient.setBirthDate(LocalDate.parse(dto.getBirthDate(), formatter));
            } catch (Exception e) {
                patient.setBirthDate(null);
            }
        } else {
            patient.setBirthDate(null);
        }

        patient.setHeightCm(parseDouble(dto.getHeightCm()));
        patient.setWeightKg(parseDouble(dto.getWeightKg()));
        patient.setBmi(parseDouble(dto.getBmi()));

        patient.setCategory(dto.getCategory());
        patient.setMedicalDone(dto.getMedicalDone());
        patient.setDentalDone(dto.getDentalDone());
        patient.setContactNumber(dto.getContactNumber());
        patient.setHealthExamForm(dto.getHealthExamForm());
        patient.setMedicalDentalInfoSheet(dto.getMedicalDentalInfoSheet());
        patient.setDentalChart(dto.getDentalChart());
        patient.setSpecialMedicalCondition(dto.getSpecialMedicalCondition());
        patient.setCommunicableDisease(dto.getCommunicableDisease());
        patient.setEmergencyContactName(dto.getEmergencyContactName());
        patient.setEmergencyContactRelationship(dto.getEmergencyContactRelationship());
        patient.setEmergencyContactNumber(dto.getEmergencyContactNumber());
        patient.setRemarks(dto.getRemarks());

        return patient;
    }

    public void importPatientsFromCsv(MultipartFile file) throws IOException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try (Reader reader = new InputStreamReader(file.getInputStream())) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .build();

            try (CSVParser csvParser = new CSVParser(reader, format)) {
                for (CSVRecord record : csvParser) {
                    String studentNumber = record.get("Student Number");
                    Patients patient = patientsRepository.findByStudentNumber(studentNumber)
                            .orElse(new Patients());

                    patient.setStudentNumber(studentNumber);
                    patient.setLastName(record.get("Last Name"));
                    patient.setFirstName(record.get("First Name"));
                    patient.setMiddleInitial(record.get("MI"));
                    patient.setStatus(record.get("Status"));
                    patient.setGender(record.get("Gender"));

                    String birthDate = record.get("Birthdate");
                    if (birthDate != null && !birthDate.isBlank()) {
                        try {
                            patient.setBirthDate(LocalDate.parse(birthDate, formatter));
                        } catch (Exception e) {
                            patient.setBirthDate(null);
                        }
                    }

                    patient.setHeightCm(parseDouble(record.get("Ht. (cm)")));
                    patient.setWeightKg(parseDouble(record.get("Wt. (Kg)")));
                    patient.setBmi(parseDouble(record.get("BMI")));

                    patient.setCategory(record.get("Category"));
                    patient.setMedicalDone(record.get("Medical Done"));
                    patient.setDentalDone(record.get("Dental Done"));
                    patient.setContactNumber(record.get("Contact Number"));
                    patient.setHealthExamForm(record.get("Health Exam Form for School Entry"));
                    patient.setMedicalDentalInfoSheet(record.get("Medical/Dental Info Sheet"));
                    patient.setDentalChart(record.get("Dental Chart"));
                    patient.setSpecialMedicalCondition(record.get("Special Medical Condition"));
                    patient.setCommunicableDisease(record.get("Communicable Dse"));
                    patient.setEmergencyContactName(record.get("Contact Person in case of Emergency"));
                    patient.setEmergencyContactRelationship(record.get("Relationship to Contact Person"));
                    patient.setEmergencyContactNumber(record.get("Contact Number of Contact Person"));
                    patient.setRemarks(record.get("Remarks"));

                    patientsRepository.save(patient);
                }
            }
        }
    }

    public void exportPatients(HttpServletResponse response) throws IOException {
        response.setHeader("Content-Disposition", "attachment; filename=patients.csv");
        List<Patients> patients = patientsRepository.findAll();

        try (PrintWriter writer = response.getWriter()) {
            writer.println("ID,Student Number,Last Name,First Name,MI,Status,Gender,Birthdate,Ht. (cm),Wt. (Kg),BMI,Category,Medical Done,Dental Done,Contact Number,Health Exam Form for School Entry,Medical/Dental Info Sheet,Dental Chart,Special Medical Condition,Communicable Dse,Contact Person in case of Emergency,Relationship to Contact Person,Contact Number of Contact Person,Remarks");

            for (Patients p : patients) {
                writer.printf("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        p.getId(),
                        safe(p.getStudentNumber()),
                        safe(p.getLastName()),
                        safe(p.getFirstName()),
                        safe(p.getMiddleInitial()),
                        safe(p.getStatus()),
                        safe(p.getGender()),
                        p.getBirthDate() != null ? p.getBirthDate().toString() : "",
                        safeNum(p.getHeightCm()),
                        safeNum(p.getWeightKg()),
                        safeNum(p.getBmi()),
                        safe(p.getCategory()),
                        safe(p.getMedicalDone()),
                        safe(p.getDentalDone()),
                        safe(p.getContactNumber()),
                        safe(p.getHealthExamForm()),
                        safe(p.getMedicalDentalInfoSheet()),
                        safe(p.getDentalChart()),
                        safe(p.getSpecialMedicalCondition()),
                        safe(p.getCommunicableDisease()),
                        safe(p.getEmergencyContactName()),
                        safe(p.getEmergencyContactRelationship()),
                        safe(p.getEmergencyContactNumber()),
                        safe(p.getRemarks())
                );
            }
        }
    }

    private Double parseDouble(String value) {
        try {
            return (value != null && !value.isBlank()) ? Double.parseDouble(value.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String safe(String value) {
        return (value != null) ? value.replace(",", " ") : "";
    }

    private String safeNum(Double value) {
        return (value != null) ? String.format("%.2f", value) : "";
    }
}