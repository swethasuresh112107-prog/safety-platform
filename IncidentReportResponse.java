package com.guardianvoice.dto;

import com.guardianvoice.model.IncidentReport;
import java.time.Instant;

public record IncidentReportResponse(
    Long id,
    String title,
    String category,
    String description,
    Double lat,
    Double lng,
    String address,
    Instant occurredAt,
    String status,
    boolean syncedFromOffline,
    Instant createdAt
) {
    public static IncidentReportResponse from(IncidentReport r) {
        return new IncidentReportResponse(
            r.getId(), r.getTitle(), r.getCategory().name(), r.getDescription(),
            r.getLat(), r.getLng(), r.getAddress(), r.getOccurredAt(),
            r.getStatus().name(), r.isSyncedFromOffline(), r.getCreatedAt()
        );
    }
}
