package com.guardianvoice.dto;

public record SosRequest(
    Double lat,
    Double lng,
    String message,
    String nearestStationName,
    String nearestStationPhone,
    Double stationLat,
    Double stationLng
) {}
