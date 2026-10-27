package com.helmet.dto;

import com.helmet.model.Alert;
import com.helmet.model.AlertSeverity;
import com.helmet.model.AlertStatus;
import com.helmet.model.AlertType;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter  
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
        return dto;
    }
}
