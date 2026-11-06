package com.helmet.config;

import com.helmet.model.Device;
import com.helmet.model.ThresholdSettings;
import com.helmet.repository.DeviceRepository;
import com.helmet.repository.ThresholdSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final DeviceRepository deviceRepository;
    private final ThresholdSettingsRepository thresholdRepository;

    public DataInitializer(DeviceRepository deviceRepository,
                           ThresholdSettingsRepository thresholdRepository) {
        this.deviceRepository = deviceRepository;
        this.thresholdRepository = thresholdRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        initDeviceWithSettings("HELMET_001", "Mũ Bảo Hộ Thông Minh 01", "Nguyễn Văn An");
        log.info("DataInitializer completed.");
    }

    private void initDeviceWithSettings(String deviceUid, String name, String workerName) {
        if (deviceRepository.findByDeviceUid(deviceUid).isEmpty()) {
            Device dev = Device.builder()
                    .deviceUid(deviceUid)
                    .name(name)
                    .workerName(workerName)
                    .isOnline(true)
                    .isWorn(true)
                    .buzzerOn(false)
                    .lastLat(21.0285)
                    .lastLng(105.8542)
                    .lastSeenAt(Instant.now())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            deviceRepository.save(dev);
            log.info("Initialized default device '{}'.", deviceUid);
        } else {
            log.info("Device '{}' already exists, skipping.", deviceUid);
        }

        if (thresholdRepository.findByDeviceUid(deviceUid).isEmpty()) {
            ThresholdSettings settings = ThresholdSettings.builder()
                    .deviceUid(deviceUid)
                    .gasWarn(300.0)
                    .gasDanger(600.0)
                    .fallSensitivity(2.5)
                    .fallInactivitySec(15)
                    .offlineTimeoutSec(10)
                    .telemetrySampleSec(5)
                    .isActive(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            thresholdRepository.save(settings);
            log.info("Initialized threshold settings for device '{}'.", deviceUid);
        } else {
            log.info("Threshold for '{}' already exists, skipping.", deviceUid);
        }
    }
}
