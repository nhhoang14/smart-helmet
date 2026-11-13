package com.helmet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommandRequest {

    /**
     * Action theo Bảng 3.6: FIND_HELMET, ACK_ALERT, SET_THRESHOLD, REQUEST_STATE
     */
    private String action;

    private Map<String, Object> payload;

    // Backward compatibility for buzzer toggle
    private Boolean on;
    private String state;
    private Integer time;
}
