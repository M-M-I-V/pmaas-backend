package dev.mmiv.pmaas.dto;

public record PatientList(
    int id,
    String firstName,
    String lastName,
    String studentNumber,
    String gender,
    String status
) {}