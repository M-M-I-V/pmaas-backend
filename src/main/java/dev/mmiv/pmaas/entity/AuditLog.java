package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String entityName;
    private int recordId;

    private String action;
    private String username;
    private LocalDateTime timestamp;

    @Column(columnDefinition = "TEXT")
    private String details;
}