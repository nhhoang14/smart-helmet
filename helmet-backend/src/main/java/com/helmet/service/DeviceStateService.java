package com.helmet.service;

import com.helmet.dto.AlertDto;
import com.helmet.dto.DeviceStateDto;
import com.helmet.model.AlertType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Quản lý trạng thái tức thời và sự cố đang diễn ra của mũ bảo hộ theo thiết kế Redis
 * (Bảng 3.12: device:state:{deviceUid} và device:alert:active:{deviceUid}).
 * Đảm bảo Dashboard và REST API đọc tức thời mà không phải truy vấn liên tục vào MySQL.
 */
@Service
public class DeviceStateService {
    private static final Logger log = LoggerFactory.getLogger(DeviceStateService.class);

    // device:state:{deviceUid} -> DeviceStateDto
    private final Map<String, DeviceStateDto> stateStore = new ConcurrentHashMap<>();

    // device:alert:active:{deviceUid} -> Hash (alert_type -> AlertDto)
    private final Map<String, Map<AlertType, AlertDto>> activeAlertStore = new ConcurrentHashMap<>();

    public DeviceStateDto getDeviceState(String deviceUid) {
        return stateStore.computeIfAbsent(deviceUid, uid -> DeviceStateDto.builder()
                .deviceUid(uid)
                .id(uid)
                .name("Mũ #" + uid)
                .status("OFFLINE")
                .isOnline(false)
                .isWorn(false)
                .alertLevel("NORMAL")
                .buzzerState("OFF")
                .buzzerOn(false)
                .accMagnitude(1.0)
                .gasPpm(0.0)
                .lastSeenAt(null)
                .createdAt(Instant.now())
                .build());
    }

    public synchronized DeviceStateDto updateDeviceState(String deviceUid, Consumer<DeviceStateDto> updater) {
        DeviceStateDto state = getDeviceState(deviceUid);
        updater.accept(state);
        stateStore.put(deviceUid, state);
        return state;
    }

    public void saveDeviceState(String deviceUid, DeviceStateDto state) {
        stateStore.put(deviceUid, state);
    }

    public synchronized void addActiveAlert(String deviceUid, AlertDto alert) {
        Map<AlertType, AlertDto> map = activeAlertStore.computeIfAbsent(deviceUid, k -> new ConcurrentHashMap<>());
        map.put(alert.getType(), alert);

        // Cập nhật mức alert_level tương ứng trong trạng thái tức thời
        updateDeviceState(deviceUid, state -> {
            if (alert.getType() == AlertType.GAS_DANGER || alert.getType() == AlertType.FALL || alert.getType() == AlertType.SOS) {
                state.setAlertLevel("DANGER");
            } else if (alert.getType() == AlertType.GAS_WARNING && !"DANGER".equals(state.getAlertLevel())) {
                state.setAlertLevel("WARNING");
            }
        });
        log.info("Redis Hash [device:alert:active:{}]: Added active alert {}", deviceUid, alert.getType());
    }

    public synchronized void removeActiveAlert(String deviceUid, AlertType alertType) {
        Map<AlertType, AlertDto> map = activeAlertStore.get(deviceUid);
        if (map != null) {
            map.remove(alertType);
            log.info("Redis Hash [device:alert:active:{}]: Removed alert {}", deviceUid, alertType);

            // Đánh giá lại alert_level tổng thể của thiết bị
            updateDeviceState(deviceUid, state -> {
                if (map.isEmpty()) {
                    state.setAlertLevel("NORMAL");
                } else if (map.containsKey(AlertType.GAS_DANGER) || map.containsKey(AlertType.FALL) || map.containsKey(AlertType.SOS)) {
                    state.setAlertLevel("DANGER");
                } else if (map.containsKey(AlertType.GAS_WARNING)) {
                    state.setAlertLevel("WARNING");
                } else {
                    state.setAlertLevel("NORMAL");
                }
            });
        }
    }

    public Map<AlertType, AlertDto> getActiveAlerts(String deviceUid) {
        return activeAlertStore.getOrDefault(deviceUid, Collections.emptyMap());
    }

    public void clearActiveAlerts(String deviceUid) {
        activeAlertStore.remove(deviceUid);
        updateDeviceState(deviceUid, state -> state.setAlertLevel("NORMAL"));
    }
}
