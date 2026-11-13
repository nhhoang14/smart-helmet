package com.helmet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceStateDto {

    @JsonProperty("device_uid")
    private String deviceUid;

    // Backward compatibility id alias
    private String id;
    private String name;
    private String workerName;

    @Builder.Default
    @JsonProperty("status")
    private String status = "OFFLINE"; // ONLINE, OFFLINE

    @Builder.Default
    @JsonProperty("is_online")
    private boolean isOnline = false;

    @Builder.Default
    @JsonProperty("is_worn")
    private boolean isWorn = false;

    @Builder.Default
    @JsonProperty("alert_level")
    private String alertLevel = "NORMAL"; // NORMAL, WARNING, DANGER

    @Builder.Default
    @JsonProperty("buzzer_state")
    private String buzzerState = "OFF"; // ON, OFF

    @Builder.Default
    @JsonProperty("buzzer_on")
    private boolean buzzerOn = false;

    @JsonProperty("sensors")
    @Builder.Default
    private Map<String, Object> sensors = new HashMap<>();

    @JsonProperty("gps")
    @Builder.Default
    private Map<String, Object> gps = new HashMap<>();

    @JsonProperty("thresholds")
    @Builder.Default
    private Map<String, Object> thresholds = new HashMap<>();

    @JsonProperty("gas_ppm")
    private Double gasPpm;

    @Builder.Default
    @JsonProperty("acc_magnitude")
    private Double accMagnitude = 1.0;

    @JsonProperty("last_lat")
    private Double lastLat;

    @JsonProperty("last_lng")
    private Double lastLng;

    @JsonProperty("last_seen_at")
    private Instant lastSeenAt;

    @JsonProperty("timestamp")
    private Long timestamp;

    @JsonProperty("created_at")
    private Instant createdAt;

    public String getId() {
        return id != null ? id : deviceUid;
    }

    public void setId(String id) {
        this.id = id;
        if (this.deviceUid == null) {
            this.deviceUid = id;
        }
    }

    public void setDeviceUid(String deviceUid) {
        this.deviceUid = deviceUid;
        if (this.id == null) {
            this.id = deviceUid;
        }
    }

    public void setOnline(boolean online) {
        this.isOnline = online;
        this.status = online ? "ONLINE" : "OFFLINE";
    }

    public void setBuzzerOn(boolean buzzerOn) {
        this.buzzerOn = buzzerOn;
        this.buzzerState = buzzerOn ? "ON" : "OFF";
    }

    public void setBuzzerState(String buzzerState) {
        this.buzzerState = buzzerState;
        this.buzzerOn = "ON".equalsIgnoreCase(buzzerState);
    }

    public void setStatus(String status) {
        this.status = status;
        this.isOnline = "ONLINE".equalsIgnoreCase(status);
    }
}
