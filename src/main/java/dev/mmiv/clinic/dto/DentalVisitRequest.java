package dev.mmiv.clinic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DentalVisitRequest {
    private String visitDate;
    private String visitType;
    private String chiefComplaint;
    private String temperature;
    private String bloodPressure;
    private String pulseRate;
    private String respiratoryRate;
    private String spo2;
    private String history;
    private String symptoms;
    private String physicalExamFindings;
    private String diagnosis;
    private String plan;
    private String treatment;
    private String diagnosticTestResult;
    private String toothStatus;
    private int patientId;
}