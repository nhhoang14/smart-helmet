package com.helmet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryMessage {
    private Double gas;
    private Boolean worn;
    private Boolean fall;
    private Double lat;
    private Double lng;
    private Long ts;

    public TelemetryMessage() {}

    public TelemetryMessage(Double gas, Boolean worn, Boolean fall, Double lat, Double lng, Long ts) {
        this.gas = gas;
        this.worn = worn;
        this.fall = fall;
        this.lat = lat;
        this.lng = lng;
        this.ts = ts;
    }

    public Double getGas() { return gas; }
    public void setGas(Double gas) { this.gas = gas; }

    public Boolean getWorn() { return worn; }
    public void setWorn(Boolean worn) { this.worn = worn; }

    public Boolean getFall() { return fall; }
    public void setFall(Boolean fall) { this.fall = fall; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public Long getTs() { return ts; }
    public void setTs(Long ts) { this.ts = ts; }
}
