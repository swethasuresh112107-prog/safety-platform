package com.guardianvoice.repository;

import com.guardianvoice.model.Emergency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SosRepository extends JpaRepository<Emergency, Long> {
    List<Emergency> findByUserIdOrderByTimestampDesc(Long userId);
    Optional<Emergency> findByIdAndUserId(Long id, Long userId);
}
