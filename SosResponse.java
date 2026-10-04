package com.guardianvoice.dto;

import java.time.Instant;
import com.guardianvoice.model.Emergency;

public record SosResponse(
    Long id,
    String status,
    Instant timestamp,
    Double lat,
    Double lng,
    String message,
    String nearestStationName,
    String nearestStationPhone,
    Double stationLat,
    Double stationLng
) {
    public static SosResponse from(Emergency e) {
        return new SosResponse(
            e.getId(), e.getStatus().name(), e.getTimestamp(), e.getLat(), e.getLng(), e.getMessage(),
            e.getNearestStationName(), e.getNearestStationPhone(), e.getStationLat(), e.getStationLng()
        );
    }
}
