package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.VisitsList;
import dev.mmiv.pmaas.entity.Visits;
import dev.mmiv.pmaas.service.VisitsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VisitsController {

    private final VisitsService visitsService;

    @GetMapping("/visits")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<List<Visits>> getVisits() {
        return new ResponseEntity<>(visitsService.getVisits(), HttpStatus.OK);
    }

    @GetMapping("/visits-list")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<List<VisitsList>> getVisitsList() {
        return ResponseEntity.ok(visitsService.getVisitsList());
    }

    @GetMapping("/visits-list/patient/{id}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<List<VisitsList>> getVisitsListByPatient(@PathVariable int id) {
        return ResponseEntity.ok(visitsService.getVisitsListByPatientId(id));
    }

    @PostMapping("/visits/import")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<String> importVisits(@RequestParam("file") MultipartFile file) throws IOException {
        visitsService.importVisits(file);
        return ResponseEntity.ok("Visits imported successfully!");
    }

    @GetMapping("/visits/export")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public void exportVisits(HttpServletResponse response) throws IOException {
        visitsService.exportVisits(response);
    }
}