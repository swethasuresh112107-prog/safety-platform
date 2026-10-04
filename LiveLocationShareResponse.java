package com.guardianvoice.dto;

import com.guardianvoice.model.LiveLocationSession;
import java.time.Instant;

/**
 * Deliberately minimal: this is served with no auth to whoever holds the
 * share link (a contact), so it exposes only what they need -- the sharer's
 * first name, current position, and whether the share is still live. No
 * email, phone, or account info.
 */
public record LiveLocationShareResponse(
    String sharerName,
    String status,
    Double lat,
    Double lng,
    Instant updatedAt
) {
    public static LiveLocationShareResponse from(LiveLocationSession s) {
        String fullName = s.getUser().getName();
        String firstName = fullName == null || fullName.isBlank()
            ? "Someone"
            : fullName.trim().split("\\s+")[0];
        return new LiveLocationShareResponse(
            firstName, s.getStatus().name(), s.getLat(), s.getLng(), s.getUpdatedAt()
        );
    }
}
