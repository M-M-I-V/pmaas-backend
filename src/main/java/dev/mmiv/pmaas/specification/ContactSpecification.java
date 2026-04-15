package dev.mmiv.pmaas.specification;

import dev.mmiv.pmaas.dto.ContactFilterRequest;
import dev.mmiv.pmaas.entity.Contact;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a composable JPA Specification from a ContactFilterRequest.
 *
 * Each filter is null-safe — if the filter value is null or blank,
 * no predicate is added and the field is unrestricted.
 *
 * All string comparisons use LOWER(field) LIKE LOWER('%value%') for
 * case-insensitive partial matching without PostgreSQL-specific functions,
 * keeping the implementation database-agnostic and SQL-injection-safe
 * (values go through bind parameters, never string concatenation).
 */
public final class ContactSpecification {

    private ContactSpecification() {}

    public static Specification<Contact> from(ContactFilterRequest filter) {
        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            // Partial match: name
            if (hasText(filter.name())) {
                predicates.add(cb.like(
                        cb.lower(root.get("name")),
                        toLikePattern(filter.name())
                ));
            }

            // Exact match: designation (case-insensitive)
            if (hasText(filter.designation())) {
                predicates.add(cb.equal(
                        cb.lower(root.get("designation")),
                        filter.designation().trim().toLowerCase()
                ));
            }

            // Exact match: visitType
            if (filter.visitType() != null) {
                predicates.add(cb.equal(root.get("visitType"), filter.visitType()));
            }

            // Exact match: modeOfCommunication
            if (filter.modeOfCommunication() != null) {
                predicates.add(cb.equal(
                        root.get("modeOfCommunication"),
                        filter.modeOfCommunication()
                ));
            }

            // Exact match: purpose (case-insensitive)
            if (hasText(filter.purpose())) {
                predicates.add(cb.equal(
                        cb.lower(root.get("purpose")),
                        filter.purpose().trim().toLowerCase()
                ));
            }

            // Exact match: respond
            if (filter.respond() != null) {
                predicates.add(cb.equal(root.get("respond"), filter.respond()));
            }

            // Partial match: contactNumber
            if (hasText(filter.contactNumber())) {
                predicates.add(cb.like(
                        cb.lower(root.get("contactNumber")),
                        toLikePattern(filter.contactNumber())
                ));
            }

            // Partial match: remarks
            if (hasText(filter.remarks())) {
                predicates.add(cb.like(
                        cb.lower(root.get("remarks")),
                        toLikePattern(filter.remarks())
                ));
            }

            // Date range
            if (filter.fromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("contactDate"), filter.fromDate()
                ));
            }
            if (filter.toDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("contactDate"), filter.toDate()
                ));
            }

            // Time range
            if (filter.fromTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("contactTime"), filter.fromTime()
                ));
            }
            if (filter.toTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("contactTime"), filter.toTime()
                ));
            }

            // ── Avoid N+1 on patient join when listing
            // Only fetch join when not a count query (count queries must not
            // use fetch joins or Hibernate throws an exception).
            if (!Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                root.fetch("patient", jakarta.persistence.criteria.JoinType.LEFT);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // Helpers

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Converts a user-provided search term into a LIKE pattern.
     * The value is lowercased here; cb.lower() handles the column side.
     * Special LIKE characters (%, _) are escaped to prevent wildcard injection.
     */
    private static String toLikePattern(String value) {
        String escaped = value.trim()
                .replace("\\", "\\\\")
                .replace("%",  "\\%")
                .replace("_",  "\\_");
        return "%" + escaped.toLowerCase() + "%";
    }
}