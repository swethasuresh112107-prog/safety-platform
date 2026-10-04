package com.guardianvoice.service;

import com.guardianvoice.dto.LiveLocationSessionResponse;
import com.guardianvoice.dto.LiveLocationShareResponse;
import com.guardianvoice.dto.LiveLocationStartRequest;
import com.guardianvoice.dto.LiveLocationUpdateRequest;
import com.guardianvoice.exception.ApiException;
import com.guardianvoice.model.Emergency;
import com.guardianvoice.model.LiveLocationSession;
import com.guardianvoice.model.User;
import com.guardianvoice.repository.LiveLocationSessionRepository;
import com.guardianvoice.repository.SosRepository;
import com.guardianvoice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class LiveLocationService {

    private final LiveLocationSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final SosRepository sosRepository;

    public LiveLocationService(LiveLocationSessionRepository sessionRepository, UserRepository userRepository,
                                SosRepository sosRepository) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.sosRepository = sosRepository;
    }

    /** Starting a new session while one is already active just takes over that same session. */
    public LiveLocationSessionResponse start(Long userId, LiveLocationStartRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expired."));

        Optional<LiveLocationSession> existing =
            sessionRepository.findByUserIdAndStatus(userId, LiveLocationSession.Status.ACTIVE);
        if (existing.isPresent()) {
            LiveLocationSession session = existing.get();
            session.setLat(request.lat());
            session.setLng(request.lng());
            session.setUpdatedAt(Instant.now());
            sessionRepository.save(session);
            return LiveLocationSessionResponse.from(session);
        }

        Emergency emergency = null;
        if (request.emergencyId() != null) {
            emergency = sosRepository.findByIdAndUserId(request.emergencyId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Emergency not found."));
        }

        LiveLocationSession session = new LiveLocationSession(request.lat(), request.lng(), user, emergency);
        sessionRepository.save(session);
        return LiveLocationSessionResponse.from(session);
    }

    public LiveLocationSessionResponse update(Long userId, Long sessionId, LiveLocationUpdateRequest request) {
        LiveLocationSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Live location session not found."));

        if (session.getStatus() != LiveLocationSession.Status.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "This live location share has already ended.");
        }

        session.setLat(request.lat());
        session.setLng(request.lng());
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);
        return LiveLocationSessionResponse.from(session);
    }

    public LiveLocationSessionResponse end(Long userId, Long sessionId) {
        LiveLocationSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Live location session not found."));

        session.setStatus(LiveLocationSession.Status.ENDED);
        session.setEndedAt(Instant.now());
        sessionRepository.save(session);
        return LiveLocationSessionResponse.from(session);
    }

    public LiveLocationSessionResponse active(Long userId) {
        LiveLocationSession session = sessionRepository
            .findByUserIdAndStatus(userId, LiveLocationSession.Status.ACTIVE)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No active live location share."));
        return LiveLocationSessionResponse.from(session);
    }

    /** Public, unauthenticated -- reached only via the unguessable share token handed to a contact. */
    public LiveLocationShareResponse viewByShareToken(String token) {
        LiveLocationSession session = sessionRepository.findByShareToken(token)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This share link is invalid or has expired."));
        return LiveLocationShareResponse.from(session);
    }
}
