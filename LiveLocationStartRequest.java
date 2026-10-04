package com.guardianvoice.dto;

/** emergencyId is optional -- a live-location share can stand alone or be tied to an active SOS. */
public record LiveLocationStartRequest(
    Double lat,
    Double lng,
    Long emergencyId
) {}
