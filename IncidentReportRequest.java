package com.guardianvoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IncidentReportRequest(
    @NotBlank String title,
    @NotNull String category,
    @NotBlank String description,
    Double lat,
    Double lng,
    String address,
    String occurredAt,
    boolean syncedFromOffline
) {}
