package com.guardianvoice.service;

import com.guardianvoice.dto.IncidentReportRequest;
import com.guardianvoice.dto.IncidentReportResponse;
import com.guardianvoice.exception.ApiException;
import com.guardianvoice.model.IncidentReport;
import com.guardianvoice.model.User;
import com.guardianvoice.repository.IncidentReportRepository;
import com.guardianvoice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class IncidentReportService {

    private final IncidentReportRepository incidentReportRepository;
    private final UserRepository userRepository;

    public IncidentReportService(IncidentReportRepository incidentReportRepository, UserRepository userRepository) {
        this.incidentReportRepository = incidentReportRepository;
        this.userRepository = userRepository;
    }

    public IncidentReportResponse create(Long userId, IncidentReportRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expired."));

        IncidentReport.Category category = parseCategory(request.category());
        Instant occurredAt = parseInstant(request.occurredAt());

        IncidentReport report = new IncidentReport(
            request.title(), category, request.description(),
            request.lat(), request.lng(), request.address(),
            occurredAt, request.syncedFromOffline(), user
        );
        incidentReportRepository.save(report);
        return IncidentReportResponse.from(report);
    }

    public List<IncidentReportResponse> list(Long userId) {
        return incidentReportRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream().map(IncidentReportResponse::from).toList();
    }

    public IncidentReportResponse get(Long userId, Long reportId) {
        IncidentReport report = incidentReportRepository.findByIdAndUserId(reportId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found."));
        return IncidentReportResponse.from(report);
    }

    public void delete(Long userId, Long reportId) {
        IncidentReport report = incidentReportRepository.findByIdAndUserId(reportId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found."));
        incidentReportRepository.delete(report);
    }

    private IncidentReport.Category parseCategory(String raw) {
        if (raw == null) return IncidentReport.Category.OTHER;
        try {
            return IncidentReport.Category.valueOf(raw.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return IncidentReport.Category.OTHER;
        }
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "occurredAt must be an ISO-8601 timestamp.");
        }
    }
}
