package dev.mmiv.pmaas.dto;

import java.time.LocalDate;

public record MedicalVisitResponse(
    int id,
    LocalDate visitDate,
    String visitType,
    String chiefComplaint,
    Double temperature,
    String bloodPressure,
    Integer pulseRate,
    Integer respiratoryRate,
    Double spo2,
    String history,
    String physicalExamFindings,
    String diagnosis,
    String plan,
    String treatment,
    String hama,
    String referralForm,
    String medicalChartImage,
    String diagnosticTestResult,
    String diagnosticTestImage,
    String nursesNotes,
    String fullName,
    LocalDate birthDate
) {}
