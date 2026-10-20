package com.helmet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CommandRequest {
    private Boolean on;

    public CommandRequest() {}

    public CommandRequest(Boolean on) {
        this.on = on;
    }

    public Boolean getOn() { return on; }
    public void setOn(Boolean on) { this.on = on; }
}
