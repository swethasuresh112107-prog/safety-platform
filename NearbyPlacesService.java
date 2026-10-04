package com.guardianvoice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardianvoice.dto.NearbyPlaceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Moves the nearby-police/hospital/transport lookup server-side (previously
 * only the web frontend did this, directly against Overpass, and only for
 * police). Doing it here means Android gets the same feature for free via
 * one HTTP call, instead of re-implementing Overpass client-side in Kotlin.
 *
 * Uses the free OpenStreetMap Overpass API -- no API key. Results are cached
 * in-memory for a few minutes per (rounded location, type set) so repeated
 * requests (e.g. panning the map, or multiple users near each other) don't
 * all hit Overpass individually.
 */
@Service
public class NearbyPlacesService {

    private static final Logger log = LoggerFactory.getLogger(NearbyPlacesService.class);
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int MAX_RESULTS_PER_CATEGORY = 15;

    public static final Set<String> VALID_TYPES = Set.of("police", "hospital", "transport");

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(Instant at, List<NearbyPlaceResponse> results) {}

    public List<NearbyPlaceResponse> findNearby(double lat, double lng, Set<String> types, int radiusM) {
        Set<String> requested = types.isEmpty() ? VALID_TYPES : types;
        int radius = Math.min(Math.max(radiusM, 500), 10000);

        String cacheKey = String.format(Locale.ROOT, "%.2f,%.2f:%s:%d",
            lat, lng, String.join(",", new TreeSet<>(requested)), radius);
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && Duration.between(cached.at(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return cached.results();
        }

        List<NearbyPlaceResponse> results = queryOverpass(lat, lng, requested, radius);
        cache.put(cacheKey, new CacheEntry(Instant.now(), results));
        return results;
    }

    private List<NearbyPlaceResponse> queryOverpass(double lat, double lng, Set<String> types, int radiusM) {
        String query = buildQuery(lat, lng, types, radiusM);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OVERPASS_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("data=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Overpass returned status {}", response.statusCode());
                return List.of();
            }

            return parseElements(response.body(), lat, lng);
        } catch (Exception e) {
            log.warn("Overpass lookup failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildQuery(double lat, double lng, Set<String> types, int radiusM) {
        StringBuilder filters = new StringBuilder();
        if (types.contains("police")) {
            filters.append(String.format(Locale.ROOT,
                "node[\"amenity\"=\"police\"](around:%d,%f,%f);", radiusM, lat, lng));
        }
        if (types.contains("hospital")) {
            filters.append(String.format(Locale.ROOT,
                "node[\"amenity\"=\"hospital\"](around:%d,%f,%f);", radiusM, lat, lng));
            filters.append(String.format(Locale.ROOT,
                "way[\"amenity\"=\"hospital\"](around:%d,%f,%f);", radiusM, lat, lng));
        }
        if (types.contains("transport")) {
            filters.append(String.format(Locale.ROOT,
                "node[\"highway\"=\"bus_stop\"](around:%d,%f,%f);", radiusM, lat, lng));
            filters.append(String.format(Locale.ROOT,
                "node[\"amenity\"=\"bus_station\"](around:%d,%f,%f);", radiusM, lat, lng));
            filters.append(String.format(Locale.ROOT,
                "node[\"railway\"=\"station\"](around:%d,%f,%f);", radiusM, lat, lng));
        }
        return "[out:json][timeout:20];(" + filters + ");out center;";
    }

    private List<NearbyPlaceResponse> parseElements(String body, double originLat, double originLng) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode elements = root.path("elements");

        // Keep the top N closest per category rather than one combined cap,
        // so a dense cluster of bus stops can't crowd out the one hospital.
        Map<String, List<NearbyPlaceResponse>> byCategory = new HashMap<>();
        for (String type : VALID_TYPES) byCategory.put(type, new ArrayList<>());

        for (JsonNode el : elements) {
            JsonNode tags = el.path("tags");
            String category = categoryOf(tags);
            if (category == null) continue;

            Double lat = el.has("lat") ? el.get("lat").asDouble() : null;
            Double lng = el.has("lon") ? el.get("lon").asDouble() : null;
            if (lat == null && el.has("center")) {
                lat = el.path("center").path("lat").asDouble();
                lng = el.path("center").path("lon").asDouble();
            }
            if (lat == null || lng == null) continue;

            String name = tags.has("name") ? tags.get("name").asText() : defaultName(category);
            String phone = firstNonNull(
                tags.has("phone") ? tags.get("phone").asText() : null,
                tags.has("contact:phone") ? tags.get("contact:phone").asText() : null
            );
            double distanceKm = haversineKm(originLat, originLng, lat, lng);

            byCategory.get(category).add(new NearbyPlaceResponse(category, name, phone, lat, lng, distanceKm));
        }

        List<NearbyPlaceResponse> combined = new ArrayList<>();
        for (List<NearbyPlaceResponse> list : byCategory.values()) {
            list.sort(Comparator.comparingDouble(NearbyPlaceResponse::distanceKm));
            combined.addAll(list.subList(0, Math.min(list.size(), MAX_RESULTS_PER_CATEGORY)));
        }
        combined.sort(Comparator.comparingDouble(NearbyPlaceResponse::distanceKm));
        return combined;
    }

    private String categoryOf(JsonNode tags) {
        if (tags == null || tags.isMissingNode()) return null;
        String amenity = tags.path("amenity").asText(null);
        String highway = tags.path("highway").asText(null);
        String railway = tags.path("railway").asText(null);

        if ("police".equals(amenity)) return "police";
        if ("hospital".equals(amenity)) return "hospital";
        if ("bus_station".equals(amenity) || "bus_stop".equals(highway) || "station".equals(railway)) return "transport";
        return null;
    }

    private String defaultName(String category) {
        return switch (category) {
            case "police" -> "Police Station";
            case "hospital" -> "Hospital";
            case "transport" -> "Transport Stop";
            default -> "Nearby";
        };
    }

    private String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double r = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
