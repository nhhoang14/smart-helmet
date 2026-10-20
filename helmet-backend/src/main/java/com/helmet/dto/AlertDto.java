package com.helmet.dto;

import com.helmet.model.Alert;
import com.helmet.model.AlertSeverity;
import com.helmet.model.AlertStatus;
import com.helmet.model.AlertType;

import java.time.Instant;

public class AlertDto {
    private Long id;
    private String deviceId;
    private AlertType type;
    private AlertSeverity severity;
    private String message;
    private Double gasPpm;
    private Double lat;
    private Double lng;
    private AlertStatus status;
    private Instant createdAt;
    private Instant acknowledgedAt;
    private boolean telegramSent;

    public AlertDto() {}

    public static AlertDto fromEntity(Alert alert) {
        AlertDto dto = new AlertDto();
        dto.setId(alert.getId());
        dto.setDeviceId(alert.getDeviceId());
        dto.setType(alert.getType());
        dto.setSeverity(alert.getSeverity());
        dto.setMessage(alert.getMessage());
        dto.setGasPpm(alert.getGasPpm());
        dto.setLat(alert.getLat());
        dto.setLng(alert.getLng());
        dto.setStatus(alert.getStatus());
        dto.setCreatedAt(alert.getCreatedAt());
        dto.setAcknowledgedAt(alert.getAcknowledgedAt());
        dto.setTelegramSent(alert.isTelegramSent());
        return dto;
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

    public boolean isTelegramSent() { return telegramSent; }
    public void setTelegramSent(boolean telegramSent) { this.telegramSent = telegramSent; }
}
