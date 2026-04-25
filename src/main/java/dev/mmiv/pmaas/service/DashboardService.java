package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.DashboardDTOs.*;
import dev.mmiv.pmaas.repository.DashboardRepository;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard service layer.
 *
 * Responsibilities:
 *   1. Default null date parameters to the current month.
 *   2. Validate date ranges (start must not be after end).
 *   3. Map raw Object[] results from DashboardRepository to typed DTOs.
 *   4. Log slow queries (> 1 second) for performance monitoring.
 *   5. Never return raw entities or expose PII fields.
 *
 * All methods are read-only transactional — no write operations are
 * performed by the analytics layer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final long SLOW_QUERY_THRESHOLD_MS = 1_000L;

    private final DashboardRepository dashboardRepository;

    // KPI METHODS

    public TotalVisitsKpiDTO getTotalVisitsKpi() {
        long start = now();
        Object[] row = dashboardRepository.fetchTotalVisitsKpi();
        logIfSlow("getTotalVisitsKpi", start);
        return new TotalVisitsKpiDTO(toLong(row[0]), toLong(row[1]));
    }

    public VisitRatioDTO getVisitRatioKpi() {
        long start = now();
        Object[] row = dashboardRepository.fetchVisitRatioKpi();
        logIfSlow("getVisitRatioKpi", start);
        return new VisitRatioDTO(toLong(row[0]), toLong(row[1]));
    }

    public HighRiskPatientsKpiDTO getHighRiskPatientsKpi() {
        long start = now();
        Long count = dashboardRepository.fetchHighRiskPatientCount();
        logIfSlow("getHighRiskPatientsKpi", start);
        return new HighRiskPatientsKpiDTO(count);
    }

    public CriticalInventoryKpiDTO getCriticalInventoryKpi() {
        long start = now();
        Long count = dashboardRepository.fetchCriticalInventoryCount();
        logIfSlow("getCriticalInventoryKpi", start);
        return new CriticalInventoryKpiDTO(count);
    }

    public AppointmentsTodayKpiDTO getAppointmentsTodayKpi() {
        long start = now();
        Long count = dashboardRepository.fetchAppointmentsTodayCount();
        logIfSlow("getAppointmentsTodayKpi", start);
        return new AppointmentsTodayKpiDTO(count);
    }

    // CLINICAL ANALYTICS METHODS

    public List<TopDiagnosisDTO> getTopDiagnoses(
        LocalDate startDate,
        LocalDate endDate
    ) {
        DateRange range = resolveRange(startDate, endDate);
        long start = now();

        List<Object[]> rows = dashboardRepository.fetchTopDiagnoses(
            range.start(),
            range.end()
        );
        logIfSlow("getTopDiagnoses", start);

        return rows
            .stream()
            .map(row -> new TopDiagnosisDTO((String) row[0], toLong(row[1])))
            .toList();
    }

    public List<TopComplaintDTO> getTopComplaints(
        LocalDate startDate,
        LocalDate endDate
    ) {
        DateRange range = resolveRange(startDate, endDate);
        long start = now();

        List<Object[]> rows = dashboardRepository.fetchTopComplaints(
            range.start(),
            range.end()
        );
        logIfSlow("getTopComplaints", start);

        return rows
            .stream()
            .map(row -> new TopComplaintDTO((String) row[0], toLong(row[1])))
            .toList();
    }

    // PATIENT ACTIVITY METHODS

    public List<PatientCategoryCountDTO> getDemographicsBreakdown(
        LocalDate startDate,
        LocalDate endDate
    ) {
        DateRange range = resolveRange(startDate, endDate);
        long start = now();

        List<Object[]> rows = dashboardRepository.fetchDemographicsBreakdown(
            range.start(),
            range.end()
        );
        logIfSlow("getDemographicsBreakdown", start);

        return rows
            .stream()
            .map(row ->
                new PatientCategoryCountDTO((String) row[0], toLong(row[1]))
            )
            .toList();
    }

    public List<DailyVisitVolumeDTO> getDailyVisitVolume() {
        long start = now();
        List<Object[]> rows = dashboardRepository.fetchDailyVisitVolume();
        logIfSlow("getDailyVisitVolume", start);

        return rows
            .stream()
            .map(row ->
                new DailyVisitVolumeDTO(toLocalDate(row[0]), toLong(row[1]))
            )
            .toList();
    }

    // INVENTORY ANALYTICS METHODS

    public List<LowStockItemDTO> getLowStockItems() {
        long start = now();
        List<Object[]> rows = dashboardRepository.fetchLowStockItems();
        logIfSlow("getLowStockItems", start);

        return rows
            .stream()
            .map(row ->
                new LowStockItemDTO(
                    (String) row[0],
                    toInt(row[1]),
                    toInt(row[2])
                )
            )
            .toList();
    }

    public List<ExpiringItemDTO> getExpiringItems() {
        long start = now();
        List<Object[]> rows = dashboardRepository.fetchExpiringItems();
        logIfSlow("getExpiringItems", start);

        return rows
            .stream()
            .map(row ->
                new ExpiringItemDTO(
                    (String) row[0],
                    toLocalDate(row[1]),
                    toInt(row[2])
                )
            )
            .toList();
    }

    // CONTACTS ANALYTICS METHODS

    public List<ContactModeDistributionDTO> getContactModeDistribution() {
        long start = now();
        List<Object[]> rows =
            dashboardRepository.fetchContactModeDistribution();
        logIfSlow("getContactModeDistribution", start);

        return rows
            .stream()
            .map(row ->
                new ContactModeDistributionDTO((String) row[0], toLong(row[1]))
            )
            .toList();
    }

    public List<ContactRespondStatusDTO> getContactRespondStatus() {
        long start = now();
        List<Object[]> rows =
            dashboardRepository.fetchContactRespondDistribution();
        logIfSlow("getContactRespondStatus", start);

        return rows
            .stream()
            .map(row ->
                new ContactRespondStatusDTO((String) row[0], toLong(row[1]))
            )
            .toList();
    }

    public List<ShowRateDailyDTO> getShowRateDaily(
        Integer year,
        Integer month
    ) {
        YearMonth ym = resolveYearMonth(year, month);
        long start = now();
        List<Object[]> rows = dashboardRepository.fetchShowRateDaily(
            ym.getYear(),
            ym.getMonthValue()
        );
        logIfSlow("getShowRateDaily", start);

        return rows
            .stream()
            .map(row ->
                new ShowRateDailyDTO(
                    toLocalDate(row[0]),
                    toLong(row[1]),
                    toLong(row[2]),
                    toDouble(row[3])
                )
            )
            .toList();
    }

    public List<DailyAppointmentsDTO> getDailyAppointments() {
        long start = now();
        List<Object[]> rows = dashboardRepository.fetchDailyAppointments();
        logIfSlow("getDailyAppointments", start);

        return rows
            .stream()
            .map(row ->
                new DailyAppointmentsDTO(toLocalDate(row[0]), toLong(row[1]))
            )
            .toList();
    }

    // PRIVATE HELPERS

    /**
     * Resolves the date range, defaulting to the current calendar month
     * when either bound is null. Validates that start is not after end.
     */
    private DateRange resolveRange(LocalDate startDate, LocalDate endDate) {
        YearMonth current = YearMonth.now();
        LocalDate resolvedStart =
            startDate != null ? startDate : current.atDay(1);
        LocalDate resolvedEnd =
            endDate != null ? endDate : current.atEndOfMonth();

        if (resolvedStart.isAfter(resolvedEnd)) {
            throw new IllegalArgumentException(
                "startDate (" +
                    resolvedStart +
                    ") must not be after endDate (" +
                    resolvedEnd +
                    ")."
            );
        }
        return new DateRange(resolvedStart, resolvedEnd);
    }

    /** Resolves year/month to current YearMonth when null. */
    private YearMonth resolveYearMonth(Integer year, Integer month) {
        if (year == null || month == null) return YearMonth.now();
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException(
                "month must be between 1 and 12."
            );
        }
        return YearMonth.of(year, month);
    }

    private void logIfSlow(String method, long startMs) {
        long elapsed = System.currentTimeMillis() - startMs;
        if (elapsed > SLOW_QUERY_THRESHOLD_MS) {
            log.warn("SLOW DASHBOARD QUERY: {} took {}ms", method, elapsed);
        } else {
            log.debug("Dashboard query: {} completed in {}ms", method, elapsed);
        }
    }

    private long now() {
        return System.currentTimeMillis();
    }

    // Type-conversion helpers
    // JDBC type mapping varies between PostgreSQL driver versions:
    //   COUNT(*)          → BigInteger or Long
    //   ROUND(numeric)    → BigDecimal
    //   DATE / date cast  → java.sql.Date
    //   INTEGER           → Integer or BigDecimal
    // These helpers handle all variants defensively.

    private Long toLong(Object value) {
        if (value == null) return 0L;
        return ((Number) value).longValue();
    }

    private Integer toInt(Object value) {
        if (value == null) return 0;
        return ((Number) value).intValue();
    }

    private Double toDouble(Object value) {
        if (value == null) return 0.0;
        return ((Number) value).doubleValue();
    }

    private LocalDate toLocalDate(Object value) {
        return switch (value) {
            case null -> null;
            case LocalDate ld -> ld;
            case Date d -> d.toLocalDate();
            // PostgreSQL driver may return java.sql.Timestamp for date casts
            case java.sql.Timestamp ts -> ts.toLocalDateTime().toLocalDate();
            default -> throw new IllegalStateException(
                "Cannot convert " + value.getClass() + " to LocalDate"
            );
        };
    }

    /** Value object to carry resolved date range. */
    private record DateRange(LocalDate start, LocalDate end) {}
}
