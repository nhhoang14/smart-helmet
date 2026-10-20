package com.helmet.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_alerts_query", columnList = "device_id, status, created_at DESC")
})
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AlertStatus status = AlertStatus.NEW;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "telegram_sent", nullable = false)
    private boolean telegramSent = false;

    public Alert() {}

    public Alert(String deviceId, AlertType type, AlertSeverity severity, String message, Double gasPpm, Double lat, Double lng) {
        this.deviceId = deviceId;
        this.type = type;
        this.severity = severity;
        this.message = message;
        this.gasPpm = gasPpm;
        this.lat = lat;
        this.lng = lng;
        this.status = AlertStatus.NEW;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public AlertType getType() { return type; }
    public void setType(AlertType type) { this.type = type; }

    public AlertSeverity getSeverity() { return severity; }
    public void setSeverity(AlertSeverity severity) { this.severity = severity; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Double getGasPpm() { return gasPpm; }
    public void setGasPpm(Double gasPpm) { this.gasPpm = gasPpm; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public AlertStatus getStatus() { return status; }
    public void setStatus(AlertStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public Long getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(Long acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }

    public boolean isTelegramSent() { return telegramSent; }
    public void setTelegramSent(boolean telegramSent) { this.telegramSent = telegramSent; }
}
