package com.helmet.dto;

import java.time.Instant;

public class DeviceStateDto {
    private String id;
    private String name;
    private String zone;
    private boolean isOnline;
    private boolean isWorn;
    private boolean buzzerOn;
    private Instant lastSeenAt;
    private Double lastLat;
    private Double lastLng;
    private Double gasPpm;
    private Instant createdAt;

    public DeviceStateDto() {}

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

    public Double getGasPpm() { return gasPpm; }
    public void setGasPpm(Double gasPpm) { this.gasPpm = gasPpm; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
