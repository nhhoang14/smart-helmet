package com.helmet.service;

import com.helmet.dto.AlertDto;
import com.helmet.model.*;
import com.helmet.mqtt.MqttCommandPublisher;
import com.helmet.realtime.RealtimePublisher;
import com.helmet.repository.AlertRepository;
import com.helmet.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AlertService {
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    // Hysteresis band (đệm trễ nồng độ khí) để tránh dao động mức liên tục
    private static final double GAS_HYSTERESIS_PPM = 25.0;

    private final AlertRepository alertRepository;
    private final DeviceRepository deviceRepository;
    private final SettingsService settingsService;
    private final DeviceStateService deviceStateService;
    private final RealtimePublisher realtimePublisher;
    private final MqttCommandPublisher commandPublisher;

    public AlertService(AlertRepository alertRepository,
                        DeviceRepository deviceRepository,
                        SettingsService settingsService,
                        DeviceStateService deviceStateService,
                        RealtimePublisher realtimePublisher,
                        @Lazy MqttCommandPublisher commandPublisher) {
        this.alertRepository = alertRepository;
        this.deviceRepository = deviceRepository;
        this.settingsService = settingsService;
        this.deviceStateService = deviceStateService;
        this.realtimePublisher = realtimePublisher;
        this.commandPublisher = commandPublisher;
    }

    /**
     * Đánh giá cảnh báo khí độc theo ngưỡng gas_warn và gas_danger (Mục 3.7.3)
     */
    @Transactional
    public void evaluateGas(Device device, double gasPpm, Double lat, Double lng) {
        ThresholdSettings settings = settingsService.getThresholds(device.getUid());
        if (Boolean.FALSE.equals(settings.getIsActive())) {
            return;
        }

        double warnThreshold = settings.getGasWarn() != null ? settings.getGasWarn() : 300.0;
        double dangerThreshold = settings.getGasDanger() != null ? settings.getGasDanger() : 600.0;

        if (gasPpm >= dangerThreshold) {
            deviceStateService.updateDeviceState(device.getUid(), state -> state.setAlertLevel("DANGER"));
            List<Alert> existing = alertRepository.findByDeviceUidAndStatusAndType(
                    device.getUid(), AlertStatus.NEW, AlertType.GAS_DANGER);
            if (existing.isEmpty()) {
                String msg = String.format("Nồng độ khí độc vượt ngưỡng NGUY HIỂM: %.1f ppm (ngưỡng: %.0f ppm)",
                        gasPpm, dangerThreshold);
                createAndPublishAlert(device.getUid(), AlertType.GAS_DANGER, AlertSeverity.DANGER,
                        msg, gasPpm, lat, lng);
            }
        } else if (gasPpm >= warnThreshold) {
            deviceStateService.updateDeviceState(device.getUid(), state -> {
                if (!"DANGER".equals(state.getAlertLevel())) {
                    state.setAlertLevel("WARNING");
                }
            });
            List<Alert> existing = alertRepository.findByDeviceUidAndStatusAndType(
                    device.getUid(), AlertStatus.NEW, AlertType.GAS_WARNING);
            if (existing.isEmpty()) {
                String msg = String.format("Nồng độ khí độc mức CẢNH BÁO: %.1f ppm (ngưỡng: %.0f ppm)",
                        gasPpm, warnThreshold);
                createAndPublishAlert(device.getUid(), AlertType.GAS_WARNING, AlertSeverity.WARNING,
                        msg, gasPpm, lat, lng);
            }
        } else if (gasPpm <= (warnThreshold - GAS_HYSTERESIS_PPM)) {
            // Khí đã giảm về an toàn dưới ngưỡng có đệm trễ -> hạ alert_level trên Redis
            deviceStateService.removeActiveAlert(device.getUid(), AlertType.GAS_WARNING);
            deviceStateService.removeActiveAlert(device.getUid(), AlertType.GAS_DANGER);
        }
    }

    /**
     * Đánh giá phát hiện té ngã dựa trên NGƯỠNG gia tốc MPU6050 kết hợp trạng thái đội mũ
     * (KHÔNG áp dụng TinyML theo yêu cầu của đồ án).
     */
    @Transactional
    public void evaluateFallByThreshold(Device device, double accMagnitude, boolean isWorn, Double lat, Double lng) {
        ThresholdSettings settings = settingsService.getThresholds(device.getUid());
        if (Boolean.FALSE.equals(settings.getIsActive())) {
            return;
        }

        // Bước 0: Mũ PHẢI đang được đội (Touch Pin = true). Nếu tháo mũ hoặc rơi từ bàn -> Bỏ qua
        if (!isWorn) {
            log.debug("Mũ {} không đội, bỏ qua kiểm tra ngưỡng té ngã.", device.getUid());
            return;
        }

        // Bước 1: Xét ngưỡng gia tốc va chạm đỉnh (fall_sensitivity tính theo g, ví dụ >= 2.5g)
        double fallThreshold = settings.getFallSensitivity() != null ? settings.getFallSensitivity() : 2.5;

        if (accMagnitude >= fallThreshold) {
            // Kiểm tra xem đã có cảnh báo ngã chưa ack gần đây chưa để tránh spam
            List<Alert> existing = alertRepository.findByDeviceUidAndStatusAndType(
                    device.getUid(), AlertStatus.NEW, AlertType.FALL);
            if (existing.isEmpty()) {
                String msg = String.format("Phát hiện va chạm té ngã! Gia tốc đỉnh: %.2fg (vượt ngưỡng %.2fg)",
                        accMagnitude, fallThreshold);
                log.warn("Kích hoạt cảnh báo ngã theo ngưỡng cho mũ {}: {}", device.getUid(), msg);
                createAndPublishAlert(device.getUid(), AlertType.FALL, AlertSeverity.DANGER,
                        msg, accMagnitude, lat, lng);
            }
        }
    }

    /**
     * Tiếp nhận sự kiện trực tiếp từ MQTT (Mục 3.7.4: GAS_WARNING, GAS_DANGER, FALL, SOS)
     */
    @Transactional
    public Alert handleEvent(String deviceUid, String eventType, Double lat, Double lng) {
        return handleEventWithValue(deviceUid, eventType, null, lat, lng, null, null);
    }

    @Transactional
    public Alert handleEventWithValue(String deviceUid, String eventType, Double value, Double lat, Double lng) {
        return handleEventWithValue(deviceUid, eventType, value, lat, lng, null, null);
    }

    @Transactional
    public Alert handleEventWithValue(String deviceUid, String eventType, Double value, Double lat, Double lng,
                                      String customMessage, String locationSource) {
        AlertType type;
        AlertSeverity severity = AlertSeverity.DANGER;
        String msg;

        if ("GAS_DANGER".equalsIgnoreCase(eventType)) {
            type = AlertType.GAS_DANGER;
            msg = (customMessage != null && !customMessage.isBlank()) ? customMessage : "Cảnh báo nồng độ khí độc nguy hiểm phát hiện từ mũ!";
        } else if ("GAS_WARNING".equalsIgnoreCase(eventType)) {
            type = AlertType.GAS_WARNING;
            severity = AlertSeverity.WARNING;
            msg = (customMessage != null && !customMessage.isBlank()) ? customMessage : "Cảnh báo nồng độ khí độc tăng cao!";
        } else if ("SOS".equalsIgnoreCase(eventType)) {
            type = AlertType.SOS;
            msg = (customMessage != null && !customMessage.isBlank()) ? customMessage : "Tín hiệu SOS khẩn cấp được kích hoạt từ nút bấm trên vành mũ!";
        } else {
            type = AlertType.FALL;
            msg = (customMessage != null && !customMessage.isBlank()) ? customMessage : ((value != null)
                    ? String.format("Phát hiện té ngã tại công trường! Gia tốc đo được: %.2fg", value)
                    : "Phát hiện sự cố té ngã tại công trường!");
        }

        return createAndPublishAlert(deviceUid, type, severity, msg, value, lat, lng, locationSource);
    }

    @Transactional
    public Alert createOfflineAlert(Device device) {
        String msg = "Mũ bảo hộ #" + device.getUid() + " (" + device.getName() + ") mất kết nối (Offline timeout).";
        return createAndPublishAlert(device.getUid(), AlertType.OFFLINE, AlertSeverity.WARNING,
                msg, null, device.getLastLat(), device.getLastLng(), null);
    }

    private Alert createAndPublishAlert(String deviceUid, AlertType type, AlertSeverity severity,
                                       String message, Double value, Double lat, Double lng) {
        return createAndPublishAlert(deviceUid, type, severity, message, value, lat, lng, null);
    }

    private Alert createAndPublishAlert(String deviceUid, AlertType type, AlertSeverity severity,
                                       String message, Double value, Double lat, Double lng, String locationSource) {
        Alert alert = Alert.builder()
                .deviceUid(deviceUid)
                .type(type)
                .severity(severity)
                .message(message)
                .value(value)
                .latitude(lat)
                .longitude(lng)
                .status(AlertStatus.NEW)
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        alert = alertRepository.save(alert);

        AlertDto dto = AlertDto.fromEntity(alert);
        if (locationSource != null) {
            dto.setLocationSource(locationSource);
        }

        // Lưu vào Redis Hash: device:alert:active:{deviceUid}
        deviceStateService.addActiveAlert(deviceUid, dto);

        // Cập nhật vị trí tức thời của thiết bị và deviceState
        if (lat != null && lng != null) {
            final Double finalLat = lat;
            final Double finalLng = lng;
            deviceRepository.findByDeviceUidAndDeletedAtIsNull(deviceUid).ifPresent(d -> {
                d.setLastLat(finalLat);
                d.setLastLng(finalLng);
                deviceRepository.save(d);
            });
            deviceStateService.updateDeviceState(deviceUid, s -> {
                s.setLastLat(finalLat);
                s.setLastLng(finalLng);
                if (severity == AlertSeverity.DANGER) {
                    s.setAlertLevel("DANGER");
                }
                Map<String, Object> gps = s.getGps() != null ? s.getGps() : new HashMap<>();
                gps.put("latitude", finalLat);
                gps.put("longitude", finalLng);
                if (locationSource != null) gps.put("source", locationSource);
                s.setGps(gps);
            });
        }

        // Đẩy WebSocket tới Web Dashboard (Mục 3.7.3 bước 9)
        realtimePublisher.publishAlert(dto);
        realtimePublisher.publishState(deviceUid, deviceStateService.getDeviceState(deviceUid));

        log.warn("Created and published alert [{} - {}]: {}", deviceUid, type, message);
        return alert;
    }

    public Page<AlertDto> getAlerts(String type, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Alert> result;

        AlertType alertType = null;
        if (type != null && !type.isBlank()) {
            try { alertType = AlertType.valueOf(type.toUpperCase()); } catch (IllegalArgumentException ignored) {}
        }

        AlertStatus alertStatus = null;
        if (status != null && !status.isBlank()) {
            try { alertStatus = AlertStatus.valueOf(status.toUpperCase()); } catch (IllegalArgumentException ignored) {}
        }

        if (alertType != null && alertStatus != null) {
            result = alertRepository.findByTypeAndStatus(alertType, alertStatus, pageable);
        } else if (alertType != null) {
            result = alertRepository.findByType(alertType, pageable);
        } else if (alertStatus != null) {
            result = alertRepository.findByStatus(alertStatus, pageable);
        } else {
            result = alertRepository.findAll(pageable);
        }

        return result.map(AlertDto::fromEntity);
    }

    public List<AlertDto> getDeviceAlerts(String deviceUid) {
        return alertRepository.findByDeviceUidOrderByOccurredAtDesc(deviceUid).stream()
                .map(AlertDto::fromEntity)
                .toList();
    }

    /**
     * Xác nhận xử lý sự cố (Mục 3.7.5 Bảng 3.6 và Bảng 3.13: POST /api/v1/alerts/{alertId}/ack)
     */
    @Transactional
    public Optional<AlertDto> acknowledge(Long alertId, Long userId) {
        return alertRepository.findById(alertId).map(alert -> {
            alert.setStatus(AlertStatus.ACKNOWLEDGED);
            alert.setAcknowledgedAt(Instant.now());
            alert.setAcknowledgedBy(userId);
            Alert saved = alertRepository.save(alert);

            // Xóa trường tương ứng trong Redis Hash device:alert:active:{deviceUid} (Mục 3.7.5 bước 13)
            deviceStateService.removeActiveAlert(saved.getDeviceUid(), saved.getType());

            // Gửi lệnh ACK_ALERT xuống ESP32 qua MQTT để tắt còi báo động tại chỗ (Mục 3.7.5 bước 14)
            Map<String, Object> ackPayload = new HashMap<>();
            ackPayload.put("alert_id", alertId);
            commandPublisher.sendCommand(saved.getDeviceUid(), "ACK_ALERT", ackPayload);

            AlertDto dto = AlertDto.fromEntity(saved);
            realtimePublisher.publishAlert(dto);
            log.info("Alert #{} acknowledged for device {}", alertId, saved.getDeviceUid());
            return dto;
        });
    }
}
