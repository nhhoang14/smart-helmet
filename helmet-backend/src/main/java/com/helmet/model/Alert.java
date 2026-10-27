package com.helmet.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_alerts_query", columnList = "device_id, status, created_at DESC")
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

    @Column(name = "device_id", nullable = false, length = 32)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AlertSeverity severity;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(name = "gas_ppm")
    private Double gasPpm;

    private Double lat;
    private Double lng;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AlertStatus status = AlertStatus.NEW;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

}