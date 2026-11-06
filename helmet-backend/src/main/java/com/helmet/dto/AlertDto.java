package com.helmet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.helmet.model.Alert;
import com.helmet.model.AlertSeverity;
import com.helmet.model.AlertStatus;
import com.helmet.model.AlertType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDto {

    private Long id;

    @JsonProperty("device_id")
    private String deviceId;

    @JsonProperty("device_uid")
    private String deviceUid;

    @JsonProperty("alert_type")
    private AlertType type;

    private AlertSeverity severity;

    private String message;

    private Double value;

    @JsonProperty("gas_ppm")
    private Double gasPpm;

    private Double lat;
    private Double lng;

    private AlertStatus status;

    @JsonProperty("occurred_at")
    private Instant occurredAt;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("acknowledged_at")
    private Instant acknowledgedAt;

    @JsonProperty("acknowledged_by")
    private Long acknowledgedBy;

    public static AlertDto fromEntity(Alert a) {
        if (a == null) return null;
        return AlertDto.builder()
                .id(a.getId())
                .deviceId(a.getDeviceUid())
                .deviceUid(a.getDeviceUid())
                .type(a.getType())
                .severity(a.getSeverity())
                .message(a.getMessage())
                .value(a.getValue())
                .gasPpm(a.getValue())
                .lat(a.getLatitude())
                .lng(a.getLongitude())
                .status(a.getStatus())
                .occurredAt(a.getOccurredAt())
                .createdAt(a.getCreatedAt())
                .acknowledgedAt(a.getAcknowledgedAt())
                .acknowledgedBy(a.getAcknowledgedBy())
                .build();
    }
}
