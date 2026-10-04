package com.guardianvoice.controller;

import com.guardianvoice.dto.NearbyPlaceResponse;
import com.guardianvoice.exception.ApiException;
import com.guardianvoice.service.NearbyPlacesService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/places")
public class NearbyPlacesController {

    private final NearbyPlacesService nearbyPlacesService;

    public NearbyPlacesController(NearbyPlacesService nearbyPlacesService) {
        this.nearbyPlacesService = nearbyPlacesService;
    }

    /**
     * GET /api/places/nearby?lat=..&lng=..&types=police,hospital,transport&radius=4000
     * types and radius are optional -- types defaults to all three, radius defaults to 4000m (capped 500-10000m).
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<NearbyPlaceResponse>> nearby(
        @RequestParam Double lat,
        @RequestParam Double lng,
        @RequestParam(required = false) String types,
        @RequestParam(required = false, defaultValue = "4000") int radius
    ) {
        if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A valid lat/lng is required.");
        }

        Set<String> requestedTypes = Set.of();
        if (types != null && !types.isBlank()) {
            requestedTypes = Arrays.stream(types.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(NearbyPlacesService.VALID_TYPES::contains)
                .collect(Collectors.toSet());
        }

        return ResponseEntity.ok(nearbyPlacesService.findNearby(lat, lng, requestedTypes, radius));
    }
}
