package dev.mmiv.pmaas.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientDTO {
    private String studentNumber;
    private String lastName;
    private String firstName;
    private String middleInitial;
    private String status;
    private String gender;
    private String birthDate;
    private String heightCm;
    private String weightKg;
    private String bmi;
    private String category;
    private String medicalDone;
    private String dentalDone;
    private String contactNumber;
    private String healthExamForm;
    private String medicalDentalInfoSheet;
    private String dentalChart;
    private String specialMedicalCondition;
    private String communicableDisease;
    private String emergencyContactName;
    private String emergencyContactRelationship;
    private String emergencyContactNumber;
    private String remarks;
}