package com.helmet.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "device_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceSettings {

    @Id
    @Column(name = "device_id", length = 32)
    private String deviceId;

    @Builder.Default
    @Column(name = "gas_warn_ppm", nullable = false)
    private int gasWarnPpm = 400;
    
    @Builder.Default
    @Column(name = "gas_danger_factor", nullable = false)
    private double gasDangerFactor = 1.5;
    
    @Builder.Default
    @Column(name = "offline_timeout_sec", nullable = false)
    private int offlineTimeoutSec = 10;
    
    @Builder.Default
    @Column(name = "telemetry_sample_sec", nullable = false)
    private int telemetrySampleSec = 5;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public int dangerThreshold() {
        return (int) Math.round(gasWarnPpm * gasDangerFactor);
    }
}
