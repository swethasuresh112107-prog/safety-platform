package com.guardianvoice.model;

import jakarta.persistence.*;

@Entity
@Table(name = "contacts")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(length = 60)
    private String relation;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Column(name = "live_location_recipient", nullable = false)
    private boolean liveLocationRecipient = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public Contact() {
    }

    public Contact(String name, String phone, String relation, boolean primary, User user) {
        this.name = name;
        this.phone = phone;
        this.relation = relation;
        this.primary = primary;
        this.user = user;
    }

    public Contact(String name, String phone, String relation, boolean primary,
                    boolean liveLocationRecipient, User user) {
        this.name = name;
        this.phone = phone;
        this.relation = relation;
        this.primary = primary;
        this.liveLocationRecipient = liveLocationRecipient;
        this.user = user;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }

    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }

    public boolean isLiveLocationRecipient() { return liveLocationRecipient; }
    public void setLiveLocationRecipient(boolean liveLocationRecipient) { this.liveLocationRecipient = liveLocationRecipient; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
