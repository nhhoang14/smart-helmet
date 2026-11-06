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

    private Double value;

    private Map<String, Object> gps;

    private Double lat;
    private Double lng;
    private Long ts;
    private Long timestamp;

    public String resolveType() {
        if (alertType != null && !alertType.isBlank()) return alertType;
        if (type != null && !type.isBlank()) return type;
        return "FALL";
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

    public Long resolveTimestamp() {
        if (timestamp != null) return timestamp;
        if (ts != null) return ts;
        return System.currentTimeMillis();
    }
}
