package com.helmet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StateMessage {

    @JsonProperty("device_uid")
    private String deviceUid;

    @JsonProperty("status")
    private String status;

    @JsonProperty("is_worn")
    private Boolean isWorn;

    @JsonProperty("alert_level")
    private String alertLevel;

    @JsonProperty("buzzer_state")
    private String buzzerState;

    @JsonProperty("sensors")
    private Map<String, Object> sensors;

    @JsonProperty("thresholds")
    private Map<String, Object> thresholds;

    @JsonProperty("timestamp")
    private Long timestamp;

    @JsonProperty("last_seen")
    private Long lastSeen;
}

