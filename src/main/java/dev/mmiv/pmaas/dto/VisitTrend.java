package dev.mmiv.pmaas.dto;

import java.time.LocalDate;

public record VisitTrend(LocalDate date, Long count) {}