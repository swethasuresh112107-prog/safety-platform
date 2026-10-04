package com.guardianvoice.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "incident_reports")
public class IncidentReport {

    public enum Category { HARASSMENT, THEFT, ASSAULT, SUSPICIOUS_ACTIVITY, ACCIDENT, UNSAFE_AREA, OTHER }

    public enum Status { SUBMITTED, UNDER_REVIEW, RESOLVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category = Category.OTHER;

    @Column(nullable = false, length = 2000)
    private String description;

    private Double lat;
    private Double lng;

    @Column(length = 250)
    private String address;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SUBMITTED;

    /**
     * True when this report was created offline on the device and later
     * synced once connectivity returned. Kept for transparency in the UI --
     * see ReportRequest/IncidentReportResponse.
     */
    @Column(name = "synced_from_offline", nullable = false)
    private boolean syncedFromOffline = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public IncidentReport() {
    }

    public IncidentReport(String title, Category category, String description, Double lat, Double lng,
                           String address, Instant occurredAt, boolean syncedFromOffline, User user) {
        this.title = title;
        this.category = category;
        this.description = description;
        this.lat = lat;
        this.lng = lng;
        this.address = address;
        this.occurredAt = occurredAt;
        this.syncedFromOffline = syncedFromOffline;
        this.user = user;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public boolean isSyncedFromOffline() { return syncedFromOffline; }
    public void setSyncedFromOffline(boolean syncedFromOffline) { this.syncedFromOffline = syncedFromOffline; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
