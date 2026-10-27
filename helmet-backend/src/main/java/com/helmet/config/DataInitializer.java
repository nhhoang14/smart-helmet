package com.helmet.config;

import com.helmet.model.Device;
import com.helmet.model.DeviceSettings;
import com.helmet.repository.DeviceRepository;
import com.helmet.repository.DeviceSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final DeviceRepository deviceRepository;
    private final DeviceSettingsRepository settingsRepository;

    public DataInitializer(DeviceRepository deviceRepository, DeviceSettingsRepository settingsRepository) {
        this.deviceRepository = deviceRepository;
        this.settingsRepository = settingsRepository;
    }

    @Override
    public void run(String... args) {
        if (!deviceRepository.existsById("01")) {
            Device dev = Device.builder()
                    .id("01")
                    .name("Mũ #01")
                    .zone("Khu vực hầm lò")
                    .build();
            dev.setOnline(true);
            dev.setWorn(true);
            dev.setBuzzerOn(false);
            dev.setLastLat(21.0285);
            dev.setLastLng(105.8542);
            dev.setLastSeenAt(Instant.now());
            deviceRepository.save(dev);
            log.info("Initialized default device '01' in MySQL database.");
        }

        if (!settingsRepository.existsById("01")) {
            DeviceSettings settings = DeviceSettings.builder()
                    .deviceId("01")
                    .updatedAt(Instant.now())
                    .build();
            settings.setGasWarnPpm(400);
            settings.setGasDangerFactor(1.5);
            settings.setOfflineTimeoutSec(10);
            settings.setTelemetrySampleSec(5);
            settings.setUpdatedAt(Instant.now());
            settingsRepository.save(settings);
            log.info("Initialized default settings for device '01' in MySQL database.");
        }
    }
}
