package com.helmet.service;

import com.helmet.dto.SettingsDto;
import com.helmet.dto.ThresholdSettingsDto;
import com.helmet.model.ThresholdSettings;
import com.helmet.mqtt.MqttCommandPublisher;
import com.helmet.repository.ThresholdSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class SettingsService {
    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final ThresholdSettingsRepository thresholdRepository;
    private final MqttCommandPublisher commandPublisher;

    public SettingsService(ThresholdSettingsRepository thresholdRepository,
                           @Lazy MqttCommandPublisher commandPublisher) {
        this.thresholdRepository = thresholdRepository;
        this.commandPublisher = commandPublisher;
    }

    public ThresholdSettings getThresholds(String deviceUid) {
        return thresholdRepository.findByDeviceUid(deviceUid)
                .orElseGet(() -> {
                    ThresholdSettings ts = ThresholdSettings.builder()
                            .deviceUid(deviceUid)
                            .gasWarn(300.0)
                            .gasDanger(600.0)
                            .fallSensitivity(2.5) // Ngưỡng gia tốc va chạm 2.5g (dùng ngưỡng thay cho TinyML)
                            .fallInactivitySec(15)
                            .offlineTimeoutSec(10)
                            .telemetrySampleSec(5)
                            .isActive(true)
                            .updatedAt(Instant.now())
                            .build();
                    return thresholdRepository.save(ts);
                });
    }

    public ThresholdSettings getSettings(String deviceId) {
        return getThresholds(deviceId);
    }

    @Transactional
    public ThresholdSettings updateThresholds(String deviceUid, ThresholdSettingsDto dto) {
        ThresholdSettings ts = getThresholds(deviceUid);
        if (dto.getGasWarn() != null) ts.setGasWarn(dto.getGasWarn());
        if (dto.getGasDanger() != null) ts.setGasDanger(dto.getGasDanger());
        if (dto.getFallSensitivity() != null) ts.setFallSensitivity(dto.getFallSensitivity());
        if (dto.getFallInactivitySec() != null) ts.setFallInactivitySec(dto.getFallInactivitySec());
        if (dto.getIsActive() != null) ts.setIsActive(dto.getIsActive());
        ts.setUpdatedAt(Instant.now());
        ThresholdSettings saved = thresholdRepository.save(ts);

        syncThresholdToDevice(deviceUid, saved);
        return saved;
    }

    @Transactional
    public ThresholdSettings updateSettings(String deviceId, SettingsDto dto) {
        ThresholdSettings ts = getThresholds(deviceId);
        ts.setGasWarn((double) dto.getGasWarnPpm());
        if (dto.getGasDangerPpm() != null) {
            ts.setGasDanger(dto.getGasDangerPpm());
        } else {
            ts.setGasDanger(dto.getGasWarnPpm() * dto.getGasDangerFactor());
        }
        if (dto.getFallSensitivity() != null) {
            ts.setFallSensitivity(dto.getFallSensitivity());
        }
        ts.setOfflineTimeoutSec(dto.getOfflineTimeoutSec());
        ts.setTelemetrySampleSec(dto.getTelemetrySampleSec());
        ts.setUpdatedAt(Instant.now());
        ThresholdSettings saved = thresholdRepository.save(ts);

        syncThresholdToDevice(deviceId, saved);
        return saved;
    }

    public void syncThresholdToDevice(String deviceUid, ThresholdSettings ts) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("gas_warn", ts.getGasWarn());
            payload.put("gas_danger", ts.getGasDanger());
            payload.put("fall_sensitivity", ts.getFallSensitivity());
            commandPublisher.sendCommand(deviceUid, "SET_THRESHOLD", payload);
            log.info("Synced thresholds to helmet {}: gas_warn={}, gas_danger={}, fall_sensitivity={}",
                    deviceUid, ts.getGasWarn(), ts.getGasDanger(), ts.getFallSensitivity());
        } catch (Exception e) {
            log.warn("Could not sync thresholds via MQTT: {}", e.getMessage());
        }
    }
}
