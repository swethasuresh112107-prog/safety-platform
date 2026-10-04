package com.guardianvoice.dto;

import com.guardianvoice.model.LiveLocationSession;
import java.time.Instant;

public record LiveLocationSessionResponse(
    Long id,
    String status,
    Double lat,
    Double lng,
    Instant startedAt,
    Instant updatedAt,
    Instant endedAt,
    String shareToken,
    Long emergencyId
) {
    public static LiveLocationSessionResponse from(LiveLocationSession s) {
        return new LiveLocationSessionResponse(
            s.getId(), s.getStatus().name(), s.getLat(), s.getLng(),
            s.getStartedAt(), s.getUpdatedAt(), s.getEndedAt(), s.getShareToken(),
            s.getEmergency() != null ? s.getEmergency().getId() : null
        );
    }
}
