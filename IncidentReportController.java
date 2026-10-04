package com.guardianvoice.controller;

import com.guardianvoice.dto.IncidentReportRequest;
import com.guardianvoice.dto.IncidentReportResponse;
import com.guardianvoice.security.CurrentUser;
import com.guardianvoice.service.IncidentReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
public class IncidentReportController {

    private final IncidentReportService incidentReportService;

    public IncidentReportController(IncidentReportService incidentReportService) {
        this.incidentReportService = incidentReportService;
    }

    @PostMapping
    public ResponseEntity<IncidentReportResponse> create(@Valid @RequestBody IncidentReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentReportService.create(CurrentUser.id(), request));
    }

    @GetMapping
    public ResponseEntity<List<IncidentReportResponse>> list() {
        return ResponseEntity.ok(incidentReportService.list(CurrentUser.id()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentReportResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(incidentReportService.get(CurrentUser.id(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        incidentReportService.delete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
