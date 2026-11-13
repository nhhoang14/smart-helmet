package com.helmet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryMessage {

    // Định dạng lồng nhau theo chuẩn smart-helmet-spec.md
    @JsonProperty("sensors")
    private Map<String, Object> sensors;

    @JsonProperty("gps")
    private Map<String, Object> gps;

    @JsonProperty("is_worn")
    private Boolean isWorn;

    @JsonProperty("timestamp")
    private Long timestamp;

    // Định dạng phẳng (hỗ trợ tương thích ngược)
    private Double gas;
    private Boolean worn;
    private Boolean fall;
    private Double lat;
    private Double lng;
    private Long ts;

    @JsonProperty("location_source")
    private String locationSource;

    @JsonProperty("acc_magnitude")
    private Double flatAccMagnitude;

    public Double getGasPpm() {
        if (sensors != null && sensors.get("gas_ppm") != null) {
            Object val = sensors.get("gas_ppm");
            if (val instanceof Number) return ((Number) val).doubleValue();
        }
        return gas;
    }

    public Double getAccMagnitude() {
        if (sensors != null && sensors.get("acc_magnitude") != null) {
            Object val = sensors.get("acc_magnitude");
            if (val instanceof Number) return ((Number) val).doubleValue();
        }
        return flatAccMagnitude != null ? flatAccMagnitude : 1.0;
    }

    public Double getLatitude() {
        if (gps != null && gps.get("latitude") != null) {
            Object val = gps.get("latitude");
            if (val instanceof Number) return ((Number) val).doubleValue();
        }
        return lat;
    }

    public Double getLongitude() {
        if (gps != null && gps.get("longitude") != null) {
            Object val = gps.get("longitude");
            if (val instanceof Number) return ((Number) val).doubleValue();
        }
        return lng;
    }

    public Boolean getGpsFix() {
        if (gps != null && gps.get("fix") != null) {
            Object val = gps.get("fix");
            if (val instanceof Boolean) return (Boolean) val;
        }
        return getLatitude() != null && getLongitude() != null;
    }

    public String resolveLocationSource() {
        if (locationSource != null && !locationSource.isBlank()) {
            return locationSource;
        }
        if (gps != null && gps.get("source") != null) {
            return gps.get("source").toString();
        }
        return Boolean.TRUE.equals(getGpsFix()) ? "gps" : "wifi";
    }

    public Boolean getWornStatus() {
        if (isWorn != null) return isWorn;
        if (worn != null) return worn;
        return true;
    }

    public Long getTimestampEpoch() {
        if (timestamp != null) return timestamp;
        if (ts != null) return ts;
        return System.currentTimeMillis();
    }
}
