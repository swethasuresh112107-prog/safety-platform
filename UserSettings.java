package com.guardianvoice.model;

import jakarta.persistence.*;

/**
 * Mirrors the fields the frontend previously kept only in localStorage
 * (gv_settings): sosKeyword, fakeCallKeyword, callerName, listenEnabled,
 * trackingEnabled. One row per user, created lazily on first read/write.
 */
@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "sos_keyword", length = 60)
    private String sosKeyword = "help me";

    @Column(name = "fake_call_keyword", length = 60)
    private String fakeCallKeyword = "call me";

    @Column(name = "caller_name", length = 120)
    private String callerName = "Mom";

    @Column(name = "listen_enabled", nullable = false)
    private boolean listenEnabled = false;

    @Column(name = "tracking_enabled", nullable = false)
    private boolean trackingEnabled = false;

    public UserSettings() {
    }

    public UserSettings(User user) {
        this.user = user;
        this.userId = user.getId();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getSosKeyword() { return sosKeyword; }
    public void setSosKeyword(String sosKeyword) { this.sosKeyword = sosKeyword; }

    public String getFakeCallKeyword() { return fakeCallKeyword; }
    public void setFakeCallKeyword(String fakeCallKeyword) { this.fakeCallKeyword = fakeCallKeyword; }

    public String getCallerName() { return callerName; }
    public void setCallerName(String callerName) { this.callerName = callerName; }

    public boolean isListenEnabled() { return listenEnabled; }
    public void setListenEnabled(boolean listenEnabled) { this.listenEnabled = listenEnabled; }

    public boolean isTrackingEnabled() { return trackingEnabled; }
    public void setTrackingEnabled(boolean trackingEnabled) { this.trackingEnabled = trackingEnabled; }
}
