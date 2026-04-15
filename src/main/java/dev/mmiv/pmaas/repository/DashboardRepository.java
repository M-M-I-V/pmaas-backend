package dev.mmiv.pmaas.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Central analytics repository for the Dashboard module.
 *
 * WHY A CLASS, NOT AN INTERFACE:
 * The dashboard aggregates data across visits, patients, contacts, and
 * inventory_items in a single request. JpaRepository<Entity, ID> is bound
 * to a single entity type. Using EntityManager with native SQL here is the
 * correct design — it handles cross-table aggregations, PostgreSQL-specific
 * FILTER clauses, generate_series, and conditional counts that would require
 * ugly workarounds in JPQL.
 *
 * PERFORMANCE CONVENTIONS:
 * - All queries touch only indexed columns in their WHERE clauses.
 * - KPI queries use FILTER (WHERE ...) to aggregate multiple metrics in one
 *   round-trip instead of issuing N separate COUNT queries per page load.
 * - Daily series queries use generate_series to guarantee complete 30-day
 *   arrays with zero-fill, preventing sparse results reaching the frontend.
 * - All queries are read-only (Hibernate readOnly hint applied).
 *
 * COLUMN/TABLE ASSUMPTIONS (verify against your Flyway migrations):
 *   visits           : id, visit_date, visit_type, diagnosis, chief_complaint, patient_id
 *   patients         : id, category, special_medical_condition, communicable_disease
 *   contacts         : id, contact_date, mode_of_communication, respond, patient_id
 *   inventory_items  : id, item_name, stocks_on_hand, expiration_date, minimum_stock_level
 */
@Slf4j
@Repository
public class DashboardRepository {

    private static final String HINT_READ_ONLY = "org.hibernate.readOnly";
    private static final int    DEFAULT_TOP_N  = 10;
    private static final int    MIN_STOCK_LEVEL = 10;
    private static final int    EXPIRY_DAYS     = 60;
    private static final int    SERIES_DAYS     = 30;

    @PersistenceContext
    private EntityManager em;

    // KPI QUERIES

    /**
     * Returns today's visit count AND the month-to-date count in a single
     * SQL round-trip using PostgreSQL FILTER aggregation.
     * Result: Object[] { Long todayCount, Long monthToDateCount }
     */
    public Object[] fetchTotalVisitsKpi() {
        String sql = """
            SELECT
              COUNT(*) FILTER (WHERE visit_date = CURRENT_DATE)                             AS today_count,
              COUNT(*) FILTER (WHERE DATE_TRUNC('month', visit_date) =
                                     DATE_TRUNC('month', CURRENT_DATE))                     AS mtd_count
            FROM visits
            """;
        return (Object[]) em.createNativeQuery(sql)
                .setHint(HINT_READ_ONLY, "true")
                .getSingleResult();
    }

    /**
     * Returns medical and dental visit counts for the current month.
     * Result: Object[] { Long medicalCount, Long dentalCount }
     */
    public Object[] fetchVisitRatioKpi() {
        String sql = """
            SELECT
              COUNT(*) FILTER (WHERE visit_type = 'MEDICAL') AS medical_count,
              COUNT(*) FILTER (WHERE visit_type = 'DENTAL')  AS dental_count
            FROM visits
            WHERE DATE_TRUNC('month', visit_date) = DATE_TRUNC('month', CURRENT_DATE)
            """;
        return (Object[]) em.createNativeQuery(sql)
                .setHint(HINT_READ_ONLY, "true")
                .getSingleResult();
    }

    /**
     * Count of patients with at least one flagged risk field.
     * Touches only the patients table — no PII columns selected.
     */
    public Long fetchHighRiskPatientCount() {
        String sql = """
            SELECT COUNT(*)
            FROM patients
            WHERE special_medical_condition IS NOT NULL
               OR communicable_disease      IS NOT NULL
            """;
        return toLong(em.createNativeQuery(sql)
                .setHint(HINT_READ_ONLY, "true")
                .getSingleResult());
    }

    /**
     * Count of inventory items with zero stock.
     */
    public Long fetchCriticalInventoryCount() {
        String sql = "SELECT COUNT(*) FROM inventory_items WHERE stocks_on_hand = 0";
        return toLong(em.createNativeQuery(sql)
                .setHint(HINT_READ_ONLY, "true")
                .getSingleResult());
    }

    /**
     * Count of contact records for today (appointments scheduled today).
     */
    public Long fetchAppointmentsTodayCount() {
        String sql = "SELECT COUNT(*) FROM contacts WHERE contact_date = CURRENT_DATE";
        return toLong(em.createNativeQuery(sql)
                .setHint(HINT_READ_ONLY, "true")
                .getSingleResult());
    }

    // CLINICAL ANALYTICS QUERIES

    /**
     * Top N most frequent diagnosis values in the given date range.
     * Empty/null diagnosis values are excluded.
     * Result rows: Object[] { String diagnosis, Long count }
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> fetchTopDiagnoses(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT
              diagnosis,
              COUNT(*) AS cnt
            FROM visits
            WHERE visit_date BETWEEN :startDate AND :endDate
              AND diagnosis IS NOT NULL
              AND TRIM(diagnosis) <> ''
            GROUP BY diagnosis
            ORDER BY cnt DESC
            LIMIT :topN
            """;
        return em.createNativeQuery(sql)
                .setParameter("startDate", startDate)
                .setParameter("endDate",   endDate)
                .setParameter("topN",      DEFAULT_TOP_N)
                .setHint(HINT_READ_ONLY, "true")
                .getResultList();
    }

    /**
     * Top N most frequent chief complaint values in the given date range.
     * Result rows: Object[] { String complaint, Long count }
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> fetchTopComplaints(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT
              chief_complaint,
              COUNT(*) AS cnt
            FROM visits
            WHERE visit_date BETWEEN :startDate AND :endDate
              AND chief_complaint IS NOT NULL
              AND TRIM(chief_complaint) <> ''
            GROUP BY chief_complaint
            ORDER BY cnt DESC
            LIMIT :topN
            """;
        return em.createNativeQuery(sql)
                .setParameter("startDate", startDate)
                .setParameter("endDate",   endDate)
                .setParameter("topN",      DEFAULT_TOP_N)
                .setHint(HINT_READ_ONLY, "true")
                .getResultList();
    }

    // PATIENT ACTIVITY QUERIES

    /**
     * Visit counts grouped by patient category for the given date range.
     * Category is a classification label (Student / Faculty / Staff) — not PII.
     * Result rows: Object[] { String category, Long count }
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> fetchDemographicsBreakdown(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT
              COALESCE(p.category, 'Unknown') AS category,
              COUNT(v.id)                      AS cnt
            FROM visits v
            JOIN patients p ON p.id = v.patient_id
            WHERE v.visit_date BETWEEN :startDate AND :endDate
            GROUP BY p.category
            ORDER BY cnt DESC
            """;
        return em.createNativeQuery(sql)
                .setParameter("startDate", startDate)
                .setParameter("endDate",   endDate)
                .setHint(HINT_READ_ONLY, "true")
                .getResultList();
    }

    /**
     * Daily visit counts for the last 30 days.
     *
     * Uses generate_series to produce a complete date series so days with
     * zero visits are included. Without this, sparse results require the
     * frontend to gap-fill — that is a data responsibility, not a UI one.
     *
     * Result rows: Object[] { java.sql.Date date, Long visitCount }
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> fetchDailyVisitVolume() {
        String sql = """
            SELECT
              d.day::date                                   AS visit_date,
              COUNT(v.id)                                   AS visit_count
            FROM generate_series(
                   CURRENT_DATE - INTERVAL ':days days',
                   CURRENT_DATE,
                   INTERVAL '1 day'
                 ) AS d(day)
            LEFT JOIN visits v ON v.visit_date = d.day::date
            GROUP BY d.day
            ORDER BY d.day ASC
            """.replace(":days", String.valueOf(SERIES_DAYS - 1));
        // Note: generate_series interval literals cannot use JDBC parameters;
        // the constant is a trusted integer — no injection risk.
        return em.createNativeQuery(sql)
                .setHint(HINT_READ_ONLY, "true")
                .getResultList();
    }

    // INVENTORY ANALYTICS QUERIES

    /**
     * Items at or below minimum stock level.
     * minimum_stock_level column is used if present; falls back to constant 10
     * via COALESCE so the query is backward-compatible.
     * Result rows: Object[] { String itemName, Integer stocksOnHand, Integer minLevel }
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> fetchLowStockItems() {
        String sql = """
            SELECT
              item_name,
              stocks_on_hand,
              COALESCE(minimum_stock_level, :minLevel) AS effective_min
            FROM inventory_items
            WHERE stocks_on_hand <= COALESCE(minimum_stock_level, :minLevel)
            ORDER BY stocks_on_hand ASC, item_name ASC
            """;
        return em.createNativeQuery(sql)
                .setParameter("minLevel", MIN_STOCK_LEVEL)
                .setHint(HINT_READ_ONLY, "true")
                .getResultList();
    }

    /**
     * Items expiring within the next N days (default 60).
     * Result rows: Object[] { String itemName, java.sql.Date expirationDate, Integer stocksOnHand }
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> fetchExpiringItems() {
        String sql = """
            SELECT
              item_name,
              expiration_date,
              stocks_on_hand
            FROM inventory_items
            WHERE expiration_date IS NOT NULL
              AND expiration_date BETWEEN CURRENT_DATE
                                      AND CURRENT_DATE + INTERVAL ':days days'
            ORDER BY expiration_date ASC, item_name ASC
            """.replace(":days", String.valueOf(EXPIRY_DAYS));
        return em.createNativeQuery(sql)
                .setHint(HINT_READ_ONLY, "true")
                .getResultList();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTACTS ANALYTICS QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Contact counts grouped by mode_of_communication.
     * Result rows: Object[] { String mode, Long count }
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> fetchContactModeDistribution() {
        String sql = """
            SELECT
              COALESCE(mode_of_communication, 'UNKNOWN') AS mode,
              COUNT(*)                                    AS cnt
            FROM contacts
            GROUP BY mode_of_communication
            ORDER BY cnt DESC
            """;
        return em.createNativeQuery(sql)
                .setHint(HINT_READ_ONLY, "true")
                .getResultList();
    }

    /**
     * Contact counts grouped by respond status.
     * Result rows: Object[] { String status, Long count }
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> fetchContactRespondDistribution() {
        String sql = """
            SELECT
              COALESCE(respond, 'UNKNOWN') AS status,
              COUNT(*)                     AS cnt
            FROM contacts
            GROUP BY respond
            ORDER BY cnt DESC
            """;
        return em.createNativeQuery(sql)
                .setHint(HINT_READ_ONLY, "true")
                .getResultList();
    }

    /**
     * Show rate for a given year/month.
     *
     * Logic:
     *   scheduledCount      = contacts that have a patient_id in that month
     *   completedVisitCount = of those, contacts where a visit exists for the
     *                         same patient on the same date
     *
     * The visit subquery uses DISTINCT (patient_id, visit_date) to prevent a
     * patient with two same-day visits (e.g., medical + dental) from inflating
     * the completed count against a single contact.
     *
     * Result: Object[] { Long scheduledCount, Long completedVisitCount, BigDecimal showRate }
     */
    public Object[] fetchShowRate(int year, int month) {
        String sql = """
            SELECT
              COUNT(c.id)                          AS scheduled_count,
              COUNT(v.patient_id)                  AS completed_count,
              CASE
                WHEN COUNT(c.id) = 0 THEN 0.0
                ELSE ROUND(
                       CAST(COUNT(v.patient_id) AS numeric) * 100.0 / COUNT(c.id),
                       2
                     )
              END                                  AS show_rate_pct
            FROM contacts c
            LEFT JOIN (
              SELECT DISTINCT patient_id, visit_date
              FROM visits
            ) v ON v.patient_id  = c.patient_id
               AND v.visit_date  = c.contact_date
            WHERE c.patient_id IS NOT NULL
              AND EXTRACT(YEAR  FROM c.contact_date) = :year
              AND EXTRACT(MONTH FROM c.contact_date) = :month
            """;
        return (Object[]) em.createNativeQuery(sql)
                .setParameter("year",  year)
                .setParameter("month", month)
                .setHint(HINT_READ_ONLY, "true")
                .getSingleResult();
    }

    /**
     * Daily contact (appointment) counts for the last 30 days.
     * Uses generate_series so zero-count days are included in the series.
     * Result rows: Object[] { java.sql.Date date, Long appointmentCount }
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> fetchDailyAppointments() {
        String sql = """
            SELECT
              d.day::date    AS contact_date,
              COUNT(c.id)    AS appointment_count
            FROM generate_series(
                   CURRENT_DATE - INTERVAL ':days days',
                   CURRENT_DATE,
                   INTERVAL '1 day'
                 ) AS d(day)
            LEFT JOIN contacts c ON c.contact_date = d.day::date
            GROUP BY d.day
            ORDER BY d.day ASC
            """.replace(":days", String.valueOf(SERIES_DAYS - 1));
        return em.createNativeQuery(sql)
                .setHint(HINT_READ_ONLY, "true")
                .getResultList();
    }

    // Private helpers

    /**
     * Safely converts a native query scalar result (BigDecimal, BigInteger,
     * Integer, Long) to Long. PostgreSQL COUNT returns bigint which JDBC
     * maps to BigInteger or Long depending on driver version.
     */
    private Long toLong(Object value) {
        if (value == null) return 0L;
        return ((Number) value).longValue();
    }
}