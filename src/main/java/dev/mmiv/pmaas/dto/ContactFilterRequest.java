package dev.mmiv.pmaas.dto;

import dev.mmiv.pmaas.entity.ModeOfCommunication;
import dev.mmiv.pmaas.entity.Respond;
import dev.mmiv.pmaas.entity.VisitType;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Binds all query parameters from GET /api/contacts into a single object.
 *
 * Every field is nullable — null means "no filter on this field."
 * Spring MVC binds @RequestParam values to this record automatically
 * via @ModelAttribute in the controller.
 *
 * Using a record here prevents mutation of filter state after binding.
 */
public record ContactFilterRequest(
    /** Partial, case-insensitive match on name. */
    String name,

    /** Exact match on designation (case-insensitive in spec). */
    String designation,

    /** Exact match on visit type. */
    VisitType visitType,

    /** Exact match on mode of communication. */
    ModeOfCommunication modeOfCommunication,

    /** Exact match on purpose (case-insensitive). */
    String purpose,

    /** Exact match on respond status. */
    Respond respond,

    /** Partial, case-insensitive match on contact number. */
    String contactNumber,

    /** Partial, case-insensitive match on remarks. */
    String remarks,

    /** Inclusive lower bound of the contact date range. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

    /** Inclusive upper bound of the contact date range. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

    /** Inclusive lower bound of the contact time range. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime fromTime,

    /** Inclusive upper bound of the contact time range. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime toTime
) {}
