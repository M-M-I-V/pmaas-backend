package dev.mmiv.clinic.controller;

import dev.mmiv.clinic.dto.DentalVisitRequest;
import dev.mmiv.clinic.dto.DentalVisitResponse;
import dev.mmiv.clinic.entity.DentalVisits;
import dev.mmiv.clinic.service.DentalVisitsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;

import java.util.List;

@RestController
@RequestMapping("/api/visits/dental")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class DentalVisitsController {

    private final DentalVisitsService dentalVisitsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<List<DentalVisits>> getAll() {
        return ResponseEntity.ok(dentalVisitsService.getDentalVisits());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<DentalVisitResponse> getById(@PathVariable int id) {
        return ResponseEntity.ok(dentalVisitsService.getDentalVisitResponseById(id));
    }

    @PostMapping("/add")
    @PreAuthorize("hasRole('DMD')")
    public ResponseEntity<String> add(
            @RequestParam(value = "multipartFile", required = false) MultipartFile chartFile,
            @RequestParam(value = "diagnosticFile", required = false) MultipartFile diagnosticFile,
            @ModelAttribute DentalVisitRequest request
    ) throws IOException {
        dentalVisitsService.createDentalVisits(chartFile, diagnosticFile, request);
        return ResponseEntity.ok("Dental visit successfully created.");
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasRole('DMD')")
    public ResponseEntity<String> update(
            @PathVariable int id,
            @RequestParam(value = "multipartFile", required = false) MultipartFile chartFile,
            @RequestParam(value = "diagnosticFile", required = false) MultipartFile diagnosticFile,
            @ModelAttribute DentalVisitRequest request
    ) throws IOException {
        dentalVisitsService.updateDentalVisits(id, chartFile, diagnosticFile, request);
        return ResponseEntity.ok("Dental visit successfully updated.");
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasRole('DMD')")
    public ResponseEntity<String> delete(@PathVariable int id) {
        dentalVisitsService.deleteDentalVisits(id);
        return ResponseEntity.ok("Dental visit successfully deleted.");
    }
}