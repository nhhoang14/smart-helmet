package com.helmet.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "telemetry_logs", indexes = {
    @Index(name = "idx_telemetry_device_time", columnList = "device_uid, log_time DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Telemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "device_uid", nullable = false, length = 100)
    private String deviceUid;

    @Column(name = "log_time", nullable = false)
    private Instant logTime;

    @Column(name = "gas_ppm", nullable = false)
    private Double gasPpm;

    /**
     * Độ lớn gia tốc tổng hợp (acc_magnitude) từ MPU6050 (g).
     * Dùng để xét các trường hợp: hoạt động bình thường, va chạm, nghi ngờ té ngã.
     */
    @Builder.Default
    @Column(name = "acc_magnitude")
    private Double accMagnitude = 1.0;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Builder.Default
    @Column(name = "gps_fix")
    private Boolean gpsFix = false;

    @Builder.Default
    @Column(name = "is_worn", nullable = false)
    private Boolean isWorn = true;

    @Builder.Default
    @Column(name = "fall_state", nullable = false)
    private Boolean fallState = false;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Backward compatibility aliases
    public Instant getRecordedAt() {
        return logTime != null ? logTime : createdAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.logTime = recordedAt;
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

    public String getDeviceId() {
        return deviceUid;
    }

    public void setDeviceId(String deviceId) {
        this.deviceUid = deviceId;
    }
}
