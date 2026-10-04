package com.guardianvoice.repository;

import com.guardianvoice.model.IncidentReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {
    List<IncidentReport> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<IncidentReport> findByIdAndUserId(Long id, Long userId);
}
