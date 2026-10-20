package com.helmet.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "telemetry", indexes = {
    @Index(name = "idx_telemetry_device_time", columnList = "device_id, recorded_at DESC")
})
public class Telemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 32)
    private String deviceId;

    @Column(name = "gas_ppm", nullable = false)
    private Double gasPpm;

    @Column(name = "is_worn", nullable = false)
    private boolean isWorn;

    @Column(name = "fall_state", nullable = false)
    private boolean fallState = false;

    private Double lat;
    private Double lng;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public Telemetry() {}

    public Telemetry(String deviceId, Double gasPpm, boolean isWorn, boolean fallState, Double lat, Double lng, Instant recordedAt) {
        this.deviceId = deviceId;
        this.gasPpm = gasPpm;
        this.isWorn = isWorn;
        this.fallState = fallState;
        this.lat = lat;
        this.lng = lng;
        this.recordedAt = recordedAt != null ? recordedAt : Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Double getGasPpm() { return gasPpm; }
    public void setGasPpm(Double gasPpm) { this.gasPpm = gasPpm; }

    public boolean isWorn() { return isWorn; }
    public void setWorn(boolean worn) { isWorn = worn; }

    public boolean isFallState() { return fallState; }
    public void setFallState(boolean fallState) { this.fallState = fallState; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
