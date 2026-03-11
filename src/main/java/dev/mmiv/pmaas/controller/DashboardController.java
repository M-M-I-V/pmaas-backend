package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.DiagnosisStats;
import dev.mmiv.pmaas.dto.VisitTrend;
import dev.mmiv.pmaas.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/kpis")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<Map<String, Long>> getKpis() {
        return ResponseEntity.ok(dashboardService.getKpis());
    }

    @GetMapping("/top-diagnoses")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<List<DiagnosisStats>> getTopDiagnoses() {
        return ResponseEntity.ok(dashboardService.getTopDiagnoses());
    }

    @GetMapping("/visits-trend")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<List<VisitTrend>> getVisitsTrend() {
        return ResponseEntity.ok(dashboardService.getVisitsTrend());
    }
}