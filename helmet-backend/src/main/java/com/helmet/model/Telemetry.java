package com.helmet.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "telemetry", indexes = {
    @Index(name = "idx_telemetry_device_time", columnList = "device_id, recorded_at DESC")
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

    @Column(name = "device_id", nullable = false, length = 32)
    private String deviceId;

    @Column(name = "gas_ppm", nullable = false)
    private Double gasPpm;

    @Column(name = "is_worn", nullable = false)
    private boolean isWorn;
    
    @Builder.Default
    @Column(name = "fall_state", nullable = false)
    private boolean fallState = false;

    private Double lat;
    private Double lng;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
