package com.helmet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Một lần quét Wi-Fi của mũ, dùng cho định vị fingerprint.
 * MQTT topic: helmet/device/{uid}/wifiscan  hoặc  POST /api/devices/{uid}/wifiscan
 *
 * Ví dụ: {"scan":{"34:BA:75:00:73:F2":-40,"34:BA:75:00:74:02":-67},"ts":123}
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WifiScanMessage {

    /** BSSID -> RSSI (dBm). */
    @JsonProperty("scan")
    private Map<String, Integer> scan;

    @JsonProperty("ts")
    private Long ts;
}
