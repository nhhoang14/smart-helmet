package com.helmet.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String zone;

    @Column(name = "is_online", nullable = false)
    private boolean isOnline = false;

    @Column(name = "is_worn", nullable = false)
    private boolean isWorn = false;

    @Column(name = "buzzer_on", nullable = false)
    private boolean buzzerOn = false;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_lat")
    private Double lastLat;

    @Column(name = "last_lng")
    private Double lastLng;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Device() {}

    public Device(String id, String name, String zone) {
        this.id = id;
        this.name = name;
        this.zone = zone;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    public boolean isWorn() { return isWorn; }
    public void setWorn(boolean worn) { isWorn = worn; }

    public boolean isBuzzerOn() { return buzzerOn; }
    public void setBuzzerOn(boolean buzzerOn) { this.buzzerOn = buzzerOn; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public Double getLastLat() { return lastLat; }
    public void setLastLat(Double lastLat) { this.lastLat = lastLat; }

    public Double getLastLng() { return lastLng; }
    public void setLastLng(Double lastLng) { this.lastLng = lastLng; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
