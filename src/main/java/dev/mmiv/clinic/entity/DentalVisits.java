package dev.mmiv.clinic.entity;

import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class DentalVisits extends Visits {

    private String dentalChartImage;
    private String toothStatus;
}