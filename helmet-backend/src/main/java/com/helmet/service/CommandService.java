package com.helmet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmet.model.DeviceCommand;
import com.helmet.mqtt.MqttCommandPublisher;
import com.helmet.repository.DeviceCommandRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class CommandService {
    private static final Logger log = LoggerFactory.getLogger(CommandService.class);

    private final DeviceCommandRepository commandRepository;
    private final DeviceService deviceService;
    private final DeviceStateService deviceStateService;
    private final MqttCommandPublisher commandPublisher;
    private final ObjectMapper objectMapper;

    public CommandService(DeviceCommandRepository commandRepository,
                          DeviceService deviceService,
                          DeviceStateService deviceStateService,
                          MqttCommandPublisher commandPublisher,
                          ObjectMapper objectMapper) {
        this.commandRepository = commandRepository;
        this.deviceService = deviceService;
        this.deviceStateService = deviceStateService;
        this.commandPublisher = commandPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Gửi lệnh điều khiển chuẩn xuống ESP32 (Bảng 3.6 & Luồng 3.7.5)
     */
    @Transactional
    public boolean executeCommand(String deviceUid, String action, Map<String, Object> payload) {
        if (payload == null) payload = new HashMap<>();

        String payloadJson = "{}";
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize payload: {}", e.getMessage());
        }

        DeviceCommand cmd = DeviceCommand.builder()
                .deviceId(deviceUid)
                .commandType(action)
                .payload(payloadJson)
                .status("SENT")
                .build();
        commandRepository.save(cmd);

        // Xử lý các logic nội bộ tương ứng theo action
        if ("FIND_HELMET".equalsIgnoreCase(action)) {
            String state = String.valueOf(payload.getOrDefault("state", "ON"));
            boolean on = "ON".equalsIgnoreCase(state);
            deviceService.updateBuzzerState(deviceUid, on);
            deviceStateService.updateDeviceState(deviceUid, s -> s.setBuzzerOn(on));
        }

        // Gửi qua MQTT xuống mũ
        boolean sent = commandPublisher.sendCommand(deviceUid, action, payload);
        if (!sent) {
            cmd.setStatus("FAILED");
            commandRepository.save(cmd);
        }
        return sent;
    }

    @Transactional
    public boolean setBuzzer(String deviceId, boolean on) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("state", on ? "ON" : "OFF");
        if (on) {
            payload.put("time", 10); // Mặc định bật 10 giây theo tài liệu
        }
        return executeCommand(deviceId, "FIND_HELMET", payload);
    }
}
