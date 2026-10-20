package com.helmet.service;

import com.helmet.dto.AlertDto;
import com.helmet.model.*;
import com.helmet.realtime.RealtimePublisher;
import com.helmet.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AlertService {
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final SettingsService settingsService;
    private final TelegramNotifier telegramNotifier;
    private final RealtimePublisher realtimePublisher;

    public AlertService(AlertRepository alertRepository,
                        SettingsService settingsService,
                        TelegramNotifier telegramNotifier,
                        RealtimePublisher realtimePublisher) {
        this.alertRepository = alertRepository;
        this.settingsService = settingsService;
        this.telegramNotifier = telegramNotifier;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional
    public void evaluateGas(Device device, double gasPpm, Double lat, Double lng) {
        DeviceSettings settings = settingsService.getSettings(device.getId());
        int warnThreshold = settings.getGasWarnPpm();
        int dangerThreshold = settings.dangerThreshold();

        if (gasPpm >= dangerThreshold) {
            // Kiểm tra xem đã có cảnh báo DANGER mới gần đây chưa
            List<Alert> existing = alertRepository.findByDeviceIdAndStatusAndType(
                    device.getId(), AlertStatus.NEW, AlertType.GAS_DANGER);
            if (existing.isEmpty()) {
                String msg = String.format("Nồng độ khí CO vượt ngưỡng nguy hiểm: %.0f ppm (ngưỡng: %d ppm)",
                        gasPpm, dangerThreshold);
                createAndPublishAlert(device.getId(), AlertType.GAS_DANGER, AlertSeverity.DANGER,
                        msg, gasPpm, lat, lng, settings);
            }
        } else if (gasPpm >= warnThreshold) {
            // Kiểm tra xem đã có cảnh báo WARNING mới gần đây chưa
            List<Alert> existing = alertRepository.findByDeviceIdAndStatusAndType(
                    device.getId(), AlertStatus.NEW, AlertType.GAS_WARNING);
            if (existing.isEmpty()) {
                String msg = String.format("Nồng độ khí CO tăng cao: %.0f ppm (ngưỡng: %d ppm)",
                        gasPpm, warnThreshold);
                createAndPublishAlert(device.getId(), AlertType.GAS_WARNING, AlertSeverity.WARNING,
                        msg, gasPpm, lat, lng, settings);
            }
        }
    }

    @Transactional
    public Alert handleEvent(String deviceId, String eventType, Double lat, Double lng) {
        DeviceSettings settings = settingsService.getSettings(deviceId);
        AlertType type = "SOS".equalsIgnoreCase(eventType) ? AlertType.SOS : AlertType.FALL;
        String msg = type == AlertType.SOS ? "Tín hiệu SOS khẩn cấp từ người đội mũ!" : "Phát hiện té ngã tại vị trí công trường!";

        return createAndPublishAlert(deviceId, type, AlertSeverity.DANGER, msg, null, lat, lng, settings);
    }

    @Transactional
    public Alert createOfflineAlert(Device device) {
        DeviceSettings settings = settingsService.getSettings(device.getId());
        String msg = "Mũ bảo hộ #" + device.getId() + " (" + device.getName() + ") mất kết nối.";
        return createAndPublishAlert(device.getId(), AlertType.OFFLINE, AlertSeverity.WARNING,
                msg, null, device.getLastLat(), device.getLastLng(), settings);
    }

    private Alert createAndPublishAlert(String deviceId, AlertType type, AlertSeverity severity,
                                       String message, Double gasPpm, Double lat, Double lng,
                                       DeviceSettings settings) {
        Alert alert = new Alert(deviceId, type, severity, message, gasPpm, lat, lng);
        alert = alertRepository.save(alert);

        // Gửi Telegram nếu có cấu hình
        if (settings != null && settings.getTelegramChatId() != null) {
            boolean sent = telegramNotifier.sendAlert(settings.getTelegramChatId(), alert);
            if (sent) {
                alert.setTelegramSent(true);
                alert = alertRepository.save(alert);
            }
        }

        AlertDto dto = AlertDto.fromEntity(alert);
        realtimePublisher.publishAlert(dto);
        log.warn("Created alert [{}]: {}", type, message);
        return alert;
    }

    @Transactional
    public Optional<AlertDto> acknowledge(Long alertId, Long userId) {
        return alertRepository.findById(alertId).map(alert -> {
            alert.setStatus(AlertStatus.ACKNOWLEDGED);
            alert.setAcknowledgedAt(Instant.now());
            alert.setAcknowledgedBy(userId);
            Alert saved = alertRepository.save(alert);
            AlertDto dto = AlertDto.fromEntity(saved);
            realtimePublisher.publishAlert(dto);
            return dto;
        });
    }

    public Page<AlertDto> getAlerts(String type, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.max(1, size));
        Page<Alert> result;

        boolean hasType = type != null && !type.isBlank();
        boolean hasStatus = status != null && !status.isBlank();

        if (hasType && hasStatus) {
            AlertType alertType = AlertType.valueOf(type.toUpperCase());
            AlertStatus alertStatus = AlertStatus.valueOf(status.toUpperCase());
            result = alertRepository.findByStatusAndTypeOrderByCreatedAtDesc(alertStatus, alertType, pageable);
        } else if (hasType) {
            AlertType alertType = AlertType.valueOf(type.toUpperCase());
            result = alertRepository.findByTypeOrderByCreatedAtDesc(alertType, pageable);
        } else if (hasStatus) {
            AlertStatus alertStatus = AlertStatus.valueOf(status.toUpperCase());
            result = alertRepository.findByStatusOrderByCreatedAtDesc(alertStatus, pageable);
        } else {
            result = alertRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return result.map(AlertDto::fromEntity);
    }

    public long countNewAlerts() {
        return alertRepository.countByStatus(AlertStatus.NEW);
    }
}
