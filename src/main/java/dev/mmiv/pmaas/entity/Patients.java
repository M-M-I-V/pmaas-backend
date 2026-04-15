package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Patients {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String studentNumber;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String firstName;

    private String middleInitial;

    private String status; // e.g., Active, Inactive, etc.

    private String gender;

    private LocalDate birthDate;

    private Double heightCm;
    private Double weightKg;
    private Double bmi;

    private String category; // e.g., Student, Staff, Faculty

    private String medicalDone; // Yes or No
    private String dentalDone; // Yes or No

    private String contactNumber;

    private String healthExamForm; // Yes or No
    private String medicalDentalInfoSheet; // Yes or No
    private String dentalChart; // Yes or No

    private String specialMedicalCondition;
    private String communicableDisease;

    private String emergencyContactName;
    private String emergencyContactRelationship;
    private String emergencyContactNumber;

    private String remarks;

    @OneToMany(
        mappedBy = "patient",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<Visits> visits = new ArrayList<>();

    public String getName() {
        return firstName + " " + lastName;
    }
}
