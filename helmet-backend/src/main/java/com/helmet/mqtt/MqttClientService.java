package com.helmet.mqtt;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class MqttClientService {
    private static final Logger log = LoggerFactory.getLogger(MqttClientService.class);

    @Value("${mqtt.enabled:true}")
    private boolean enabled;

    @Value("${mqtt.broker-url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.client-id:helmet-backend-server}")
    private String clientId;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @Value("${mqtt.sub-topics:helmet/+/telemetry,helmet/+/event,helmet/+/status}")
    private List<String> subTopics;

    private final MqttInboundHandler inboundHandler;
    private MqttClient client;
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();

    public MqttClientService(MqttInboundHandler inboundHandler) {
        this.inboundHandler = inboundHandler;
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("MQTT is disabled via configuration.");
            return;
        }

        reconnectExecutor.scheduleWithFixedDelay(this::tryConnect, 1, 10, TimeUnit.SECONDS);
    }

    private synchronized void tryConnect() {
        if (client != null && client.isConnected()) {
            return;
        }

        try {
            if (client == null) {
                client = new MqttClient(brokerUrl, clientId + "-" + System.currentTimeMillis() % 10000, new MemoryPersistence());
            }

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(5);
            if (username != null && !username.isBlank()) {
                options.setUserName(username);
                options.setPassword(password != null ? password.toCharArray() : new char[0]);
            }

            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("Connected to MQTT broker: {}", serverURI);
                    subscribeToTopics();
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT connection lost: {}", cause != null ? cause.getMessage() : "Unknown");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    inboundHandler.handleMessage(topic, payload);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            log.info("Attempting connection to MQTT broker at {}", brokerUrl);
            client.connect(options);
            subscribeToTopics();
        } catch (MqttException e) {
            log.info("MQTT broker at {} not reachable yet ({}), will retry...", brokerUrl, e.getMessage());
        } catch (Exception e) {
            log.warn("Error during MQTT connection attempt: {}", e.getMessage());
        }
    }

    private void subscribeToTopics() {
        if (client == null || !client.isConnected()) return;
        for (String topic : subTopics) {
            try {
                client.subscribe(topic, 1);
                log.info("Subscribed to MQTT topic: {}", topic);
            } catch (MqttException e) {
                log.error("Failed to subscribe to topic {}: {}", topic, e.getMessage());
            }
        }
    }

    public MqttClient getClient() {
        return client;
    }

    @PreDestroy
    public void stop() {
        reconnectExecutor.shutdown();
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
                client.close();
            } catch (MqttException ignored) {}
        }
    }
}
