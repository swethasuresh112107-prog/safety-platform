package com.guardianvoice.dto;

public record NearbyPlaceResponse(
    String category,
    String name,
    String phone,
    Double lat,
    Double lng,
    double distanceKm
) {}
