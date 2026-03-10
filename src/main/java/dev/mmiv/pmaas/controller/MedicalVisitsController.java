package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.MedicalVisitRequest;
import dev.mmiv.pmaas.dto.MedicalVisitResponse;
import dev.mmiv.pmaas.entity.MedicalVisits;
import dev.mmiv.pmaas.service.MedicalVisitsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/visits/medical")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class MedicalVisitsController {

    private final MedicalVisitsService medicalVisitsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<List<MedicalVisits>> getAll() {
        return ResponseEntity.ok(medicalVisitsService.getMedicalVisits());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<MedicalVisitResponse> getById(@PathVariable int id) {
        return ResponseEntity.ok(medicalVisitsService.getMedicalVisitResponseById(id));
    }

    @PostMapping("/add")
    @PreAuthorize("hasRole('MD')")
    public ResponseEntity<String> add(
            @RequestParam(value = "multipartFile", required = false) MultipartFile multipartFile,
            @RequestParam(value = "diagnosticFile", required = false) MultipartFile diagnosticFile,
            @ModelAttribute MedicalVisitRequest request
    ) throws IOException {
        medicalVisitsService.createMedicalVisits(multipartFile, diagnosticFile, request);
        return ResponseEntity.ok("Medical visit successfully created.");
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasRole('MD')")
    public ResponseEntity<String> update(
            @PathVariable int id,
            @RequestParam(value = "multipartFile", required = false) MultipartFile multipartFile,
            @RequestParam(value = "diagnosticFile", required = false) MultipartFile diagnosticFile,
            @ModelAttribute MedicalVisitRequest request
    ) throws IOException {
        medicalVisitsService.updateMedicalVisits(id, multipartFile, diagnosticFile, request);
        return ResponseEntity.ok("Medical visit successfully updated.");
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasRole('MD')")
    public ResponseEntity<String> delete(@PathVariable int id) {
        medicalVisitsService.deleteMedicalVisits(id);
        return ResponseEntity.ok("Medical visit successfully deleted.");
    }
}