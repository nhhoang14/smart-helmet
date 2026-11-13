package com.helmet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventMessage {

    @JsonProperty("alert_type")
    private String alertType;

    private String type;

    private String message;

    private Double value;

    private Double gas;

    private Map<String, Object> gps;

    private Double lat;
    private Double lng;

    @JsonProperty("location_source")
    private String locationSource;

    private String source;

    private Long ts;
    private Long timestamp;

    public String resolveType() {
        if (alertType != null && !alertType.isBlank()) return alertType;
        if (type != null && !type.isBlank()) return type;
        return "FALL";
    }

    public Double resolveValue() {
        if (value != null) return value;
        if (gas != null) return gas;
        return null;
    }

    public Double resolveLatitude() {
        if (gps != null && gps.get("latitude") != null) {
            Object val = gps.get("latitude");
            if (val instanceof Number) return ((Number) val).doubleValue();
        }
        return lat;
    }

    public Double resolveLongitude() {
        if (gps != null && gps.get("longitude") != null) {
            Object val = gps.get("longitude");
            if (val instanceof Number) return ((Number) val).doubleValue();
        }
        return lng;
    }

    public String resolveLocationSource() {
        if (locationSource != null && !locationSource.isBlank()) return locationSource;
        if (source != null && !source.isBlank()) return source;
        if (gps != null && gps.get("source") != null) return gps.get("source").toString();
        return (gps != null && Boolean.TRUE.equals(gps.get("fix"))) ? "gps" : "wifi";
    }

    public Long resolveTimestamp() {
        if (timestamp != null) return timestamp;
        if (ts != null) return ts;
        return System.currentTimeMillis();
    }
}
