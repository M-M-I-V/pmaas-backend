package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.DiagnosisStats;
import dev.mmiv.pmaas.dto.VisitTrend;
import dev.mmiv.pmaas.repository.VisitsRepository;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final VisitsRepository visitsRepository;

    public Map<String, Long> getKpis() {
        return Map.of(
                "todaysVisits", visitsRepository.countTodayVisits(),
                "visitsThisMonth", visitsRepository.countMonthVisits()
        );
    }

    public List<DiagnosisStats> getTopDiagnoses() {
        return visitsRepository.countTopDiagnosesThisMonth().stream()
                .map(r -> new DiagnosisStats((String) r[0], (Long) r[1]))
                .toList();
    }

    public List<VisitTrend> getVisitsTrend() {
        LocalDate cutoff = LocalDate.now().minusDays(30);
        return visitsRepository.countVisitsTrendLast30Days(cutoff).stream()
                .map(r -> new VisitTrend((LocalDate) r[0], (Long) r[1]))
                .toList();
    }
}