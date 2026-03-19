package dev.mmiv.pmaas.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bean Validation annotations.
 * Required fields: visitDate, visitType, chiefComplaint, patientId.
 * Vital signs are optional (patient may refuse or measurement may not apply)
 * but when provided they are validated against physiologically plausible ranges.
 * All numeric vitals arrive as Strings because the form uses multipart/form-data.
 * The service layer parses them; validation here ensures the strings are parseable
 * before they reach the service, producing a clean 400 instead of a 500.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedicalVisitRequest {

    @NotBlank(message = "Visit date is required")
    @Pattern(
        regexp = "^\\d{4}-\\d{2}-\\d{2}$",
        message = "Visit date must be in yyyy-MM-dd format"
    )
    private String visitDate;

    @NotBlank(message = "Visit type is required")
    @Pattern(
        regexp = "(?i)^(MEDICAL|DENTAL)$",
        message = "Visit type must be MEDICAL or DENTAL"
    )
    private String visitType;

    @NotBlank(message = "Chief complaint is required")
    @Size(
        max = 1000,
        message = "Chief complaint must be 1000 characters or fewer"
    )
    private String chiefComplaint;

    // Vital signs — optional but range-validated when present
    @Pattern(
        regexp = "^$|^\\d{1,3}(\\.\\d{1,2})?$",
        message = "Temperature must be a decimal number (e.g. 36.5)"
    )
    private String temperature;

    @Size(
        max = 20,
        message = "Blood pressure must be 20 characters or fewer (e.g. 120/80)"
    )
    private String bloodPressure;

    @Pattern(
        regexp = "^$|^\\d{1,3}$",
        message = "Pulse rate must be a whole number"
    )
    private String pulseRate;

    @Pattern(
        regexp = "^$|^\\d{1,3}$",
        message = "Respiratory rate must be a whole number"
    )
    private String respiratoryRate;

    @Pattern(
        regexp = "^$|^\\d{1,3}(\\.\\d{1,2})?$",
        message = "SpO2 must be a decimal number"
    )
    private String spo2;

    @Size(max = 5000, message = "History must be 5000 characters or fewer")
    private String history;

    private String symptoms;

    @Size(
        max = 5000,
        message = "Physical exam findings must be 5000 characters or fewer"
    )
    private String physicalExamFindings;

    @Size(max = 2000, message = "Diagnosis must be 2000 characters or fewer")
    private String diagnosis;

    @Size(max = 2000, message = "Plan must be 2000 characters or fewer")
    private String plan;

    @Size(max = 2000, message = "Treatment must be 2000 characters or fewer")
    private String treatment;

    @Positive(message = "Patient ID must be a positive integer")
    private int patientId;

    private String hama;
    private String referralForm;

    @Size(
        max = 2000,
        message = "Diagnostic test result must be 2000 characters or fewer"
    )
    private String diagnosticTestResult;

    @Size(max = 5000, message = "Nurses notes must be 5000 characters or fewer")
    private String nursesNotes;
}
