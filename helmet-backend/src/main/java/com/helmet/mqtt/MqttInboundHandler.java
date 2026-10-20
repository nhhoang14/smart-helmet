package com.helmet.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmet.dto.EventMessage;
import com.helmet.dto.TelemetryMessage;
import com.helmet.service.AlertService;
import com.helmet.service.DeviceService;
import com.helmet.service.TelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MqttInboundHandler {
    private static final Logger log = LoggerFactory.getLogger(MqttInboundHandler.class);

    private final TelemetryService telemetryService;
    private final AlertService alertService;
    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;

    public MqttInboundHandler(TelemetryService telemetryService,
                              AlertService alertService,
                              DeviceService deviceService,
                              ObjectMapper objectMapper) {
        this.telemetryService = telemetryService;
        this.alertService = alertService;
        this.deviceService = deviceService;
        this.objectMapper = objectMapper;
    }

    public void handleMessage(String topic, String payload) {
        try {
            String[] parts = topic.split("/");
            if (parts.length < 3 || !"helmet".equals(parts[0])) {
                log.warn("Ignored unfamiliar topic: {}", topic);
                return;
            }

            String deviceId = parts[1];
            String type = parts[2];

            switch (type) {
                case "telemetry": {
                    TelemetryMessage msg = objectMapper.readValue(payload, TelemetryMessage.class);
                    telemetryService.processTelemetry(deviceId, msg);
                    break;
                }
                case "event": {
                    EventMessage msg = objectMapper.readValue(payload, EventMessage.class);
                    alertService.handleEvent(deviceId, msg.getType(), msg.getLat(), msg.getLng());
                    break;
                }
                case "status": {
                    JsonNode node = objectMapper.readTree(payload);
                    boolean isOnline = node.has("online") && node.get("online").asBoolean();
                    deviceService.updateOnlineStatus(deviceId, isOnline);
                    break;
                }
                default:
                    log.debug("Unrecognized MQTT sub-topic: {}", topic);
            }
        } catch (Exception e) {
            log.error("Error processing MQTT message from topic [{}]: {}", topic, e.getMessage());
        }
    }
}
