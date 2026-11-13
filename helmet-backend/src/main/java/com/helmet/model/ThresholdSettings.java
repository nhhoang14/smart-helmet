package com.helmet.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "threshold_settings", indexes = {
    @Index(name = "idx_threshold_device_uid", columnList = "device_uid", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThresholdSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_uid", nullable = false, unique = true, length = 100)
    private String deviceUid;

    @Builder.Default
    @Column(name = "gas_warn", nullable = false)
    private Double gasWarn = 300.0;

    @Builder.Default
    @Column(name = "gas_danger", nullable = false)
    private Double gasDanger = 600.0;

    /**
     * Ngưỡng gia tốc va chạm để phát hiện té ngã (tính bằng g, ví dụ 2.5g).
     * Thay vì dùng mô hình TinyML, hệ thống dùng ngưỡng gia tốc đỉnh kết hợp kiểm tra đội mũ
     * và trạng thái bất động sau va chạm để phát hiện té ngã chính xác và loại bỏ báo động giả.
     */
    @Builder.Default
    @Column(name = "fall_sensitivity", nullable = false)
    private Double fallSensitivity = 2.5;

    @Builder.Default
    @Column(name = "fall_inactivity_sec", nullable = false)
    private Integer fallInactivitySec = 15;

    @Builder.Default
    @Column(name = "offline_timeout_sec", nullable = false)
    private Integer offlineTimeoutSec = 10;

    @Builder.Default
    @Column(name = "telemetry_sample_sec", nullable = false)
    private Integer telemetrySampleSec = 5;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public int getGasWarnPpm() {
        return gasWarn != null ? gasWarn.intValue() : 300;
    }

    public int dangerThreshold() {
        return gasDanger != null ? gasDanger.intValue() : 600;
    }
}

