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
    private final MqttCommandPublisher commandPublisher;
    private final ObjectMapper objectMapper;

    public CommandService(DeviceCommandRepository commandRepository,
                          DeviceService deviceService,
                          MqttCommandPublisher commandPublisher,
                          ObjectMapper objectMapper) {
        this.commandRepository = commandRepository;
        this.deviceService = deviceService;
        this.commandPublisher = commandPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public boolean setBuzzer(String deviceId, boolean on) {
        String cmdType = on ? "BUZZER_ON" : "BUZZER_OFF";
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("cmd", "BUZZER");
        payloadMap.put("on", on);

        String payloadJson = "{}";
        try {
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            log.error("Failed to serialize command payload", e);
        }

        // Tạo bản ghi command trong DB
        DeviceCommand cmd = DeviceCommand.builder()
                .deviceId(deviceId)
                .commandType(cmdType)
                .payload(payloadJson)
                .status("SENT")
                .build();
        commandRepository.save(cmd);

        // Cập nhật trạng thái thiết bị
        deviceService.updateBuzzerState(deviceId, on);

        // Publish xuống MQTT
        boolean published = commandPublisher.publishCommand(deviceId, payloadJson);
        if (!published) {
            cmd.setStatus("FAILED");
            commandRepository.save(cmd);
        }

        return true;
    }
}
