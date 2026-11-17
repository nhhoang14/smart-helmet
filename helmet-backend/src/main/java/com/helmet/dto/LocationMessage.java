package com.helmet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Vị trí trong nhà theo khu vực (zone). Hiện do WifiFingerprintService tạo từ bản quét Wi-Fi;
 * cũng có thể gửi thẳng qua MQTT helmet/device/{uid}/location hoặc POST /api/devices/{uid}/location.
 *
 * Ví dụ: {"zone":"phong_301","source":"wifi","confidence":0.8}
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationMessage {

    public static final String UNKNOWN_ZONE = "unknown";

    /** Mã khu vực, trùng với data-zone trên sơ đồ dashboard (vd: phong_301, hanh_lang). */
    @JsonProperty("zone")
    private String zone;

    /** Nguồn định vị: wifi, simulation, ... */
    @JsonProperty("source")
    private String source;

    /** Tùy chọn: RSSI (dBm) theo tên điểm phát, hiển thị kèm trên dashboard. */
    @JsonProperty("rssi")
    private Map<String, Object> rssi;

    /** Độ tin cậy 0..1 (tỉ lệ phiếu của kNN). */
    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("ts")
    private Long ts;

    public String resolveZone() {
        if (zone == null || zone.isBlank()) return UNKNOWN_ZONE;
        return zone.trim().toLowerCase();
    }

    public String resolveSource() {
        return (source == null || source.isBlank()) ? "wifi" : source.trim().toLowerCase();
    }
}
