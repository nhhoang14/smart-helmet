package com.helmet.realtime;

import com.helmet.dto.AlertDto;
import com.helmet.dto.DeviceStateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RealtimePublisher {
    private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);
    private final SimpMessagingTemplate messagingTemplate;

    public RealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishTelemetry(String deviceId, Object telemetry) {
        String destination = "/topic/devices/" + deviceId + "/telemetry";
        messagingTemplate.convertAndSend(destination, telemetry);
    }

    public void publishState(String deviceId, DeviceStateDto state) {
        String destination = "/topic/devices/" + deviceId + "/state";
        messagingTemplate.convertAndSend(destination, state);
    }

    public void publishAlert(AlertDto alert) {
        String destination = "/topic/alerts";
        messagingTemplate.convertAndSend(destination, alert);
    }
}
