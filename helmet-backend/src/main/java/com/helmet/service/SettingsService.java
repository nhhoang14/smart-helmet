package com.helmet.service;

import com.helmet.dto.SettingsDto;
import com.helmet.model.DeviceSettings;
import com.helmet.repository.DeviceSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SettingsService {
    private final DeviceSettingsRepository settingsRepository;

    public SettingsService(DeviceSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public DeviceSettings getSettings(String deviceId) {
        return settingsRepository.findById(deviceId)
                .orElseGet(() -> {
                    DeviceSettings ds = new DeviceSettings(deviceId);
                    return settingsRepository.save(ds);
                });
    }

    @Transactional
    public DeviceSettings updateSettings(String deviceId, SettingsDto dto) {
        DeviceSettings settings = getSettings(deviceId);
        settings.setGasWarnPpm(dto.getGasWarnPpm());
        settings.setGasDangerFactor(dto.getGasDangerFactor());
        settings.setOfflineTimeoutSec(dto.getOfflineTimeoutSec());
        settings.setTelemetrySampleSec(dto.getTelemetrySampleSec());
        settings.setTelegramChatId(dto.getTelegramChatId());
        settings.setUpdatedAt(Instant.now());
        return settingsRepository.save(settings);
    }
}
