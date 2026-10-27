package com.helmet.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class MqttCommandPublisher {
    private static final Logger log = LoggerFactory.getLogger(MqttCommandPublisher.class);

    private final MqttClientService mqttClientService;

    public MqttCommandPublisher(@Lazy MqttClientService mqttClientService) {
        this.mqttClientService = mqttClientService;
    }

    public boolean publishCommand(String deviceId, String payloadJson) {
        MqttClient client = mqttClientService.getClient();
        if (client == null || !client.isConnected()) {
            log.info("MQTT broker not connected. Simulated command sent for device {}: {}", deviceId, payloadJson);
            return true;
        }

        try {
            String topic = "helmet/" + deviceId + "/cmd";
            MqttMessage message = new MqttMessage(payloadJson.getBytes());
            message.setQos(1);
            client.publish(topic, message);
            log.info("Published MQTT command to {}: {}", topic, payloadJson);
            return true;
        } catch (Exception e) {
            log.error("Failed to publish MQTT command for device {}: {}", deviceId, e.getMessage());
            return false;
        }
    }
}
