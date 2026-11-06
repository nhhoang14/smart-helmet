package com.helmet.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "alert_events", indexes = {
    @Index(name = "idx_alerts_query", columnList = "device_uid, status, occurred_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "device_uid", nullable = false, length = 100)
    private String deviceUid;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 20)
    private AlertType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AlertSeverity severity;

    /**
     * Giá trị kích hoạt: nồng độ khí (ppm) hoặc độ lớn gia tốc (g) tại thời điểm xảy ra sự cố
     */
    @Column(name = "value")
    private Double value;

    @Column(length = 255)
    private String message;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private AlertStatus status = AlertStatus.NEW;

    @Builder.Default
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // Backward compatibility aliases
    public String getDeviceId() {
        return deviceUid;
    }

    public void setDeviceId(String deviceId) {
        this.deviceUid = deviceId;
    }

    public Double getGasPpm() {
        return value;
    }

    public void setGasPpm(Double gasPpm) {
        this.value = gasPpm;
    }

    public Double getLat() {
        return latitude;
    }

    public void setLat(Double lat) {
        this.latitude = lat;
    }

    public Double getLng() {
        return longitude;
    }

    public void setLng(Double lng) {
        this.longitude = lng;
    }
}
