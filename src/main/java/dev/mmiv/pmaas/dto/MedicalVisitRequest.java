package dev.mmiv.pmaas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedicalVisitRequest {
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
    private int patientId;
    private String hama;
    private String referralForm;
    private String diagnosticTestResult;
    private String nursesNotes;
}