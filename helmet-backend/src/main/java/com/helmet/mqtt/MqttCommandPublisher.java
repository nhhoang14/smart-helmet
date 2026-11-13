package com.helmet.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MqttCommandPublisher {
    private static final Logger log = LoggerFactory.getLogger(MqttCommandPublisher.class);

    private final MqttClientService mqttClientService;
    private final ObjectMapper objectMapper;

    public MqttCommandPublisher(@Lazy MqttClientService mqttClientService, ObjectMapper objectMapper) {
        this.mqttClientService = mqttClientService;
        this.objectMapper = objectMapper;
    }

    /**
     * Gửi lệnh điều khiển chuẩn xuống ESP32 theo Bảng 3.5 và Bảng 3.6 (Topic: helmet/device/{uid}/command)
     */
    public boolean sendCommand(String deviceUid, String action, Map<String, Object> payload) {
        try {
            Map<String, Object> cmd = new HashMap<>();
            cmd.put("action", action);
            cmd.put("payload", payload != null ? payload : new HashMap<>());
            String json = objectMapper.writeValueAsString(cmd);

            return publishCommand(deviceUid, json);
        } catch (Exception e) {
            log.error("Failed to serialize command for device {}: {}", deviceUid, e.getMessage());
            return false;
        }
    }

    public boolean publishCommand(String deviceId, String payloadJson) {
        MqttClient client = mqttClientService.getClient();
        if (client == null || !client.isConnected()) {
            log.info("MQTT broker not connected. Simulated command sent for device {}: {}", deviceId, payloadJson);
            return true;
        }

        try {
            // Topic chuẩn theo smart-helmet-spec.md
            String topicStandard = "helmet/device/" + deviceId + "/command";
            MqttMessage message = new MqttMessage(payloadJson.getBytes());
            message.setQos(1);
            client.publish(topicStandard, message);
            log.info("Published MQTT command to [{}]: {}", topicStandard, payloadJson);

            // Gửi thêm lên topic cũ để đảm bảo các firmware cũ cũng nhận được
            try {
                String topicLegacy = "helmet/" + deviceId + "/cmd";
                client.publish(topicLegacy, message);
            } catch (Exception ignored) {}

            return true;
        } catch (Exception e) {
            log.error("Failed to publish MQTT command for device {}: {}", deviceId, e.getMessage());
            return false;
        }
    }
}
