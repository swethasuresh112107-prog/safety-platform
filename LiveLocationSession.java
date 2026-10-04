package com.guardianvoice.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A time-boxed live-location share. Started when the user opts to share their
 * position (e.g. during/after an SOS, or a manual "share my location" action),
 * updated periodically while active, and ended explicitly -- there is no
 * silent indefinite tracking. Optionally linked to the Emergency that
 * triggered it. Sharing with contacts happens via the unguessable shareToken
 * (see LiveLocationController's public /api/location/share/{token} endpoint)
 * since contacts are phone numbers, not accounts with their own login.
 */
@Entity
@Table(name = "live_location_sessions")
public class LiveLocationSession {

    public enum Status { ACTIVE, ENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "share_token", nullable = false, unique = true, length = 64)
    private String shareToken = UUID.randomUUID().toString();

    private Double lat;
    private Double lng;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emergency_id")
    private Emergency emergency;

    public LiveLocationSession() {
    }

    public LiveLocationSession(Double lat, Double lng, User user, Emergency emergency) {
        this.lat = lat;
        this.lng = lng;
        this.user = user;
        this.emergency = emergency;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Emergency getEmergency() { return emergency; }
    public void setEmergency(Emergency emergency) { this.emergency = emergency; }
}
