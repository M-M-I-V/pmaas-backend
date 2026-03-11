package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.PatientDTO;
import dev.mmiv.pmaas.dto.PatientList;
import dev.mmiv.pmaas.entity.Patients;
import dev.mmiv.pmaas.service.PatientsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PatientsController {

    PatientsService patientsService;

    public PatientsController(PatientsService patientsService) {
        this.patientsService = patientsService;
    }

    @PostMapping("/add-patient")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<String> createPatient(@RequestBody PatientDTO dto) {
        patientsService.createPatient(dto);
        return ResponseEntity.ok("Patient created successfully");
    }

    @GetMapping("/patients-list")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<List<PatientList>> getPatientsList() {
        return new ResponseEntity<>(patientsService.getPatientsList(), HttpStatus.OK);
    }

    @GetMapping("/patients")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<List<Patients>> getAllPatients() {
        return new ResponseEntity<>(patientsService.getPatients(), HttpStatus.OK);
    }

    @GetMapping("/patients/{id}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<Patients> getAllPatientsByName(@PathVariable int id) {
        return new ResponseEntity<>(patientsService.getPatientById(id), HttpStatus.OK);
    }

    @PutMapping("/update-patient/{id}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<String> updatePatient(@PathVariable int id, @RequestBody PatientDTO dto) {
        patientsService.updatePatient(id, dto);
        return ResponseEntity.ok("Patient updated successfully");
    }

    @DeleteMapping("/delete-patient/{id}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<String> deletePatient(@PathVariable int id) {
        try {
            patientsService.deletePatient(id);
            return ResponseEntity.ok().build();

        } catch(IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e.getCause());

        } catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e.getCause());
        }
    }

    @PostMapping("/patients/import")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<String> importPatients(@RequestParam("file") MultipartFile file) {
        try {
            patientsService.importPatientsFromCsv(file);
            return ResponseEntity.ok("Patients imported successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Import failed: " + e.getMessage());
        }
    }

    @GetMapping(value = "/patients/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public void exportPatients(HttpServletResponse response) throws IOException {
        response.setHeader("Content-Disposition", "attachment; filename=patients.csv");
        patientsService.exportPatients(response);
    }
}
