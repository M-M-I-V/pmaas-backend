package dev.mmiv.pmaas.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bean Validation annotations added throughout.
 * Why this matters:
 *   Without validation, invalid data silently reaches the service and database layers.
 *   A null lastName causes a NOT NULL constraint violation at the DB level, returning
 *   a stack trace. A blank firstName gets stored. A malformed date string causes a
 *   DateTimeParseException that surfaces as a 500 with implementation details exposed.
 *   With @Valid on the controller parameter and these annotations here, Spring MVC
 *   intercepts invalid requests before the service layer is ever called and returns
 *   a structured 400 response via GlobalExceptionHandler.
 * Annotation choices:
 *   @NotBlank — rejects null, empty "", and whitespace-only strings
 *   @Size     — database column length guards (avoids truncation at the DB level)
 *   @Pattern  — ISO date format enforced before the service tries to parse it
 *   @DecimalMin/@DecimalMax — physiologically plausible vital sign ranges
 */
@Getter
@Setter
public class PatientDTO {

    @Size(
        min = 10,
        max = 10,
        message = "Student number must be 50 characters or fewer"
    )
    private String studentNumber;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must be 100 characters or fewer")
    private String lastName;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must be 100 characters or fewer")
    private String firstName;

    @Size(max = 5, message = "Middle initial must be 5 characters or fewer")
    private String middleInitial;

    @Size(max = 20, message = "Status must be 20 characters or fewer")
    private String status;

    @Size(max = 10, message = "Gender must be 10 characters or fewer")
    private String gender;

    /**
     * Expected format: yyyy-MM-dd (ISO 8601).
     * The service layer parses this; an incorrect format would throw before it
     * reaches the DB. The @Pattern here catches the error at the validation layer
     * with a clear message instead of a 500 from DateTimeParseException.
     */
    @Pattern(
        regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$",
        message = "Birth date must be in yyyy-MM-dd format"
    )
    private String birthDate;

    @DecimalMin(value = "0.0", message = "Height must be a positive number")
    @DecimalMax(value = "300.0", message = "Height must be 300 cm or less")
    private String heightCm;

    @DecimalMin(value = "0.0", message = "Weight must be a positive number")
    @DecimalMax(value = "500.0", message = "Weight must be 500 kg or less")
    private String weightKg;

    private String bmi; // Calculated field; not user-provided in most cases

    @Size(max = 30, message = "Category must be 30 characters or fewer")
    private String category;

    private String medicalDone;
    private String dentalDone;

    @Size(max = 20, message = "Contact number must be 20 characters or fewer")
    private String contactNumber;

    private String healthExamForm;
    private String medicalDentalInfoSheet;
    private String dentalChart;

    @Size(
        max = 500,
        message = "Special medical condition must be 500 characters or fewer"
    )
    private String specialMedicalCondition;

    @Size(
        max = 500,
        message = "Communicable disease field must be 500 characters or fewer"
    )
    private String communicableDisease;

    @Size(
        max = 200,
        message = "Emergency contact name must be 200 characters or fewer"
    )
    private String emergencyContactName;

    @Size(
        max = 100,
        message = "Emergency contact relationship must be 100 characters or fewer"
    )
    private String emergencyContactRelationship;

    @Size(
        max = 20,
        message = "Emergency contact number must be 20 characters or fewer"
    )
    private String emergencyContactNumber;

    @Size(max = 1000, message = "Remarks must be 1000 characters or fewer")
    private String remarks;
}
