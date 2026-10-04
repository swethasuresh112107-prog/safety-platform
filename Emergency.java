package com.guardianvoice.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "emergencies")
public class Emergency {

    public enum Status { SENT, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SENT;

    private Double lat;
    private Double lng;

    @Column(length = 500)
    private String message;

    @Column(name = "station_name", length = 200)
    private String nearestStationName;

    @Column(name = "station_phone", length = 40)
    private String nearestStationPhone;

    private Double stationLat;
    private Double stationLng;

    @Column(nullable = false, updatable = false)
    private Instant timestamp = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public Emergency() {
    }

    public Emergency(Double lat, Double lng, String message, User user) {
        this.lat = lat;
        this.lng = lng;
        this.message = message;
        this.user = user;
    }

    public Emergency(Double lat, Double lng, String message, String nearestStationName,
                      String nearestStationPhone, Double stationLat, Double stationLng, User user) {
        this.lat = lat;
        this.lng = lng;
        this.message = message;
        this.nearestStationName = nearestStationName;
        this.nearestStationPhone = nearestStationPhone;
        this.stationLat = stationLat;
        this.stationLng = stationLng;
        this.user = user;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getNearestStationName() { return nearestStationName; }
    public void setNearestStationName(String nearestStationName) { this.nearestStationName = nearestStationName; }

    public String getNearestStationPhone() { return nearestStationPhone; }
    public void setNearestStationPhone(String nearestStationPhone) { this.nearestStationPhone = nearestStationPhone; }

    public Double getStationLat() { return stationLat; }
    public void setStationLat(Double stationLat) { this.stationLat = stationLat; }

    public Double getStationLng() { return stationLng; }
    public void setStationLng(Double stationLng) { this.stationLng = stationLng; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
