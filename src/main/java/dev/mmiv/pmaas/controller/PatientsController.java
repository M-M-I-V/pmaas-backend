package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.PatientDTO;
import dev.mmiv.pmaas.dto.PatientList;
import dev.mmiv.pmaas.entity.Patients;
import dev.mmiv.pmaas.service.PatientsService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Annotation @Valid added to createPatient and updatePatient.
 *   Spring MVC will evaluate all Bean Validation constraints on PatientDTO before
 *   the method body executes. Any violation triggers a MethodArgumentNotValidException
 *   which GlobalExceptionHandler catches and returns as a structured 400 response.
 * getPatientById now throws 404 instead of returning null,
 *   so getAllPatientsByName no longer needs a null-check here.
 * Code quality: try/catch blocks removed from deletePatient and importPatients.
 *   ResponseStatusException propagates naturally to GlobalExceptionHandler which
 *   formats it consistently. Catching RuntimeException and re-wrapping it in
 *   ResponseStatusException was redundant and lost the original stack trace.
 * NOTE: @CrossOrigin removed — CORS is handled globally in WebSecurityConfiguration.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PatientsController {

    private final PatientsService patientsService;

    @PostMapping("/add-patient")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<String> createPatient(@Valid @RequestBody PatientDTO dto) {
        patientsService.createPatient(dto);
        return ResponseEntity.ok("Patient created successfully.");
    }

    @GetMapping("/patients-list")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<List<PatientList>> getPatientsList() {
        return ResponseEntity.ok(patientsService.getPatientsList());
    }

    @GetMapping("/patients")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<List<Patients>> getAllPatients() {
        return new ResponseEntity<>(patientsService.getPatients(), HttpStatus.OK);
    }

    @GetMapping("/patients/{id}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<Patients> getPatientById(@PathVariable Long id) {
        // Service now throws 404 instead of returning null.
        // No null-check needed here; ResponseStatusException propagates to GlobalExceptionHandler.
        return ResponseEntity.ok(patientsService.getPatientById(id));
    }

    @PutMapping("/update-patient/{id}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<String> updatePatient(@PathVariable Long id,
                                                @Valid @RequestBody PatientDTO dto) {
        patientsService.updatePatient(id, dto);
        return ResponseEntity.ok("Patient updated successfully.");
    }

    @DeleteMapping("/delete-patient/{id}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<String> deletePatient(@PathVariable Long id) {
        // ResponseStatusException from the service propagates to GlobalExceptionHandler.
        // No manual try/catch needed.
        patientsService.deletePatient(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/patients/import")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<String> importPatients(@RequestParam("file") MultipartFile file)
            throws IOException {
        patientsService.importPatientsFromCsv(file);
        return ResponseEntity.ok("Patients imported successfully.");
    }

    @GetMapping(value = "/patients/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public void exportPatients(HttpServletResponse response) throws IOException {
        response.setHeader("Content-Disposition", "attachment; filename=patients.csv");
        patientsService.exportPatients(response);
    }
}