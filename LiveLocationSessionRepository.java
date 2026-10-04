package com.guardianvoice.repository;

import com.guardianvoice.model.LiveLocationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LiveLocationSessionRepository extends JpaRepository<LiveLocationSession, Long> {
    Optional<LiveLocationSession> findByIdAndUserId(Long id, Long userId);
    Optional<LiveLocationSession> findByUserIdAndStatus(Long userId, LiveLocationSession.Status status);
    Optional<LiveLocationSession> findByShareToken(String shareToken);
}
