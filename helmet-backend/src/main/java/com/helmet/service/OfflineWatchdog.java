package com.helmet.service;

import com.helmet.model.Device;
import com.helmet.model.DeviceSettings;
import com.helmet.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class OfflineWatchdog {
    private static final Logger log = LoggerFactory.getLogger(OfflineWatchdog.class);

    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;
    private final SettingsService settingsService;
    private final AlertService alertService;

    public OfflineWatchdog(DeviceRepository deviceRepository,
                           DeviceService deviceService,
                           SettingsService settingsService,
                           AlertService alertService) {
        this.deviceRepository = deviceRepository;
        this.deviceService = deviceService;
        this.settingsService = settingsService;
        this.alertService = alertService;
    }

    @Scheduled(fixedDelayString = "${helmet.offline-check-interval-ms:5000}")
    public void checkOfflineDevices() {
        List<Device> onlineDevices = deviceRepository.findByIsOnlineTrue();
        Instant now = Instant.now();

        for (Device device : onlineDevices) {
            DeviceSettings settings = settingsService.getSettings(device.getId());
            int timeoutSec = settings.getOfflineTimeoutSec();

            if (device.getLastSeenAt() == null ||
                Duration.between(device.getLastSeenAt(), now).toSeconds() > timeoutSec) {
                log.warn("Device {} has timed out (last seen: {}). Marking OFFLINE.",
                        device.getId(), device.getLastSeenAt());
                deviceService.updateOnlineStatus(device.getId(), false);
                alertService.createOfflineAlert(device);
            }
        }
    }
}
