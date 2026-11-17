package com.helmet.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmet.dto.EventMessage;
import com.helmet.dto.LocationMessage;
import com.helmet.dto.StateMessage;
import com.helmet.dto.TelemetryMessage;
import com.helmet.dto.WifiScanMessage;
import com.helmet.service.AlertService;
import com.helmet.service.DeviceService;
import com.helmet.service.DeviceStateService;
import com.helmet.service.LocationService;
import com.helmet.service.SettingsService;
import com.helmet.service.TelemetryService;
import com.helmet.service.WifiFingerprintService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MqttInboundHandler {
    private static final Logger log = LoggerFactory.getLogger(MqttInboundHandler.class);

    private final TelemetryService telemetryService;
    private final AlertService alertService;
    private final DeviceService deviceService;
    private final DeviceStateService deviceStateService;
    private final SettingsService settingsService;
    private final LocationService locationService;
    private final WifiFingerprintService wifiFingerprintService;
    private final ObjectMapper objectMapper;

    /** Dùng trong unit test của nhóm: không có định vị khu vực. */
    public MqttInboundHandler(TelemetryService telemetryService,
                              AlertService alertService,
                              DeviceService deviceService,
                              DeviceStateService deviceStateService,
                              SettingsService settingsService,
                              ObjectMapper objectMapper) {
        this(telemetryService, alertService, deviceService, deviceStateService, settingsService,
                null, null, objectMapper);
    }

    @Autowired
    public MqttInboundHandler(TelemetryService telemetryService,
                              AlertService alertService,
                              DeviceService deviceService,
                              DeviceStateService deviceStateService,
                              SettingsService settingsService,
                              LocationService locationService,
                              WifiFingerprintService wifiFingerprintService,
                              ObjectMapper objectMapper) {
        this.telemetryService = telemetryService;
        this.alertService = alertService;
        this.deviceService = deviceService;
        this.deviceStateService = deviceStateService;
        this.settingsService = settingsService;
        this.locationService = locationService;
        this.wifiFingerprintService = wifiFingerprintService;
        this.objectMapper = objectMapper;
    }

    public void handleMessage(String topic, String payload) {
        try {
            String[] parts = topic.split("/");
            if (parts.length < 3 || !"helmet".equals(parts[0])) {
                log.warn("Ignored unfamiliar topic: {}", topic);
                return;
            }

            String deviceId;
            String type;

            // Xử lý chuẩn theo Bảng 3.5: helmet/device/{uid}/{messageType} (4 phần)
            if (parts.length >= 4 && "device".equals(parts[1])) {
                deviceId = parts[2];
                type = parts[3];
            } else {
                // Xử lý chuẩn cũ: helmet/{uid}/{messageType} (3 phần)
                deviceId = parts[1];
                type = parts[2];
            }

            switch (type) {
                case "telemetry": {
                    TelemetryMessage msg = objectMapper.readValue(payload, TelemetryMessage.class);
                    telemetryService.processTelemetry(deviceId, msg);
                    break;
                }
                case "alert":
                case "event": {
                    EventMessage msg = objectMapper.readValue(payload, EventMessage.class);
                    if ("FALL_ACK".equalsIgnoreCase(msg.resolveType())) {
                        alertService.acknowledgeFallFromDevice(deviceId, msg.getRelatedFallId());
                        break;
                    }
                    alertService.handleFirmwareEvent(
                            deviceId,
                            msg.resolveType(),
                            msg.resolveValue(),
                            msg.resolveLatitude(),
                            msg.resolveLongitude(),
                            msg.getMessage(),
                            msg.resolveLocationSource(),
                            msg.getEventId()
                    );
                    break;
                }
                case "status": {
                    boolean isOnline = false;
                    String clean = payload.trim();
                    if ("ONLINE".equalsIgnoreCase(clean)) {
                        isOnline = true;
                    } else if ("OFFLINE".equalsIgnoreCase(clean)) {
                        isOnline = false;
                    } else if (clean.startsWith("{")) {
                        JsonNode node = objectMapper.readTree(clean);
                        if (node.has("status")) {
                            isOnline = "ONLINE".equalsIgnoreCase(node.get("status").asText());
                        } else if (node.has("online")) {
                            isOnline = node.get("online").asBoolean();
                        }
                    }
                    deviceService.updateOnlineStatus(deviceId, isOnline);

                    // Khi thiết bị ONLINE (Mục 3.7.1 bước 9): tự động gửi lệnh SET_THRESHOLD đồng bộ ngưỡng
                    if (isOnline) {
                        settingsService.syncThresholdToDevice(deviceId, settingsService.getThresholds(deviceId));
                    }
                    break;
                }
                case "wifiscan": {
                    // Bản quét Wi-Fi của mũ -> kNN fingerprint -> khu vực
                    WifiScanMessage msg = objectMapper.readValue(payload, WifiScanMessage.class);
                    if (wifiFingerprintService != null) wifiFingerprintService.processScan(deviceId, msg);
                    break;
                }
                case "location": {
                    // Khu vực đã được tính sẵn ở nơi khác (không bắt buộc)
                    LocationMessage msg = objectMapper.readValue(payload, LocationMessage.class);
                    if (locationService != null) locationService.processLocation(deviceId, msg);
                    break;
                }
                case "state": {
                    StateMessage stateMsg = objectMapper.readValue(payload, StateMessage.class);
                    deviceStateService.updateDeviceState(deviceId, s -> {
                        if (stateMsg.getStatus() != null) s.setStatus(stateMsg.getStatus());
                        if (stateMsg.getIsWorn() != null) s.setWorn(stateMsg.getIsWorn());
                        if (stateMsg.getAlertLevel() != null) s.setAlertLevel(stateMsg.getAlertLevel());
                        if (stateMsg.getBuzzerState() != null) s.setBuzzerState(stateMsg.getBuzzerState());
                        if (stateMsg.getSensors() != null) s.setSensors(stateMsg.getSensors());
                        if (stateMsg.getThresholds() != null) s.setThresholds(stateMsg.getThresholds());
                    });
                    break;
                }
                default:
                    log.debug("Unrecognized MQTT sub-topic type: {} on topic {}", type, topic);
            }
        } catch (Exception e) {
            log.error("Error processing MQTT message from topic [{}]: {}", topic, e.getMessage());
        }
    }
}
