package com.helmet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EventMessage {
    private String type;
    private Double lat;
    private Double lng;
    private Long ts;

    public EventMessage() {}

    public EventMessage(String type, Double lat, Double lng, Long ts) {
        this.type = type;
        this.lat = lat;
        this.lng = lng;
        this.ts = ts;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public Long getTs() { return ts; }
    public void setTs(Long ts) { this.ts = ts; }
}
