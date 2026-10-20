package com.helmet.service;

import com.helmet.dto.DeviceStateDto;
import com.helmet.model.Device;
import com.helmet.model.Telemetry;
import com.helmet.realtime.RealtimePublisher;
import com.helmet.repository.DeviceRepository;
import com.helmet.repository.TelemetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DeviceService {
    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;
    private final RealtimePublisher realtimePublisher;

    public DeviceService(DeviceRepository deviceRepository,
                         TelemetryRepository telemetryRepository,
                         RealtimePublisher realtimePublisher) {
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
        this.realtimePublisher = realtimePublisher;
    }

    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    public Optional<Device> getDevice(String id) {
        return deviceRepository.findById(id);
    }

    public DeviceStateDto getDeviceStateDto(String id) {
        Device device = deviceRepository.findById(id)
                .orElseGet(() -> {
                    // Tự động tạo device 01 nếu chưa có
                    Device newDev = new Device(id, "Mũ #" + id, "Khu vực hầm lò");
                    return deviceRepository.save(newDev);
                });

        DeviceStateDto dto = toDto(device);
        // Lấy gasPpm gần nhất
        Optional<Telemetry> latestTelemetry = telemetryRepository.findTop1ByDeviceIdOrderByRecordedAtDesc(id);
        latestTelemetry.ifPresent(t -> dto.setGasPpm(t.getGasPpm()));
        return dto;
    }

    @Transactional
    public void updateOnlineStatus(String id, boolean isOnline) {
        deviceRepository.findById(id).ifPresent(device -> {
            boolean changed = device.isOnline() != isOnline;
            device.setOnline(isOnline);
            if (isOnline) {
                device.setLastSeenAt(Instant.now());
            }
            deviceRepository.save(device);

            if (changed) {
                realtimePublisher.publishState(id, toDto(device));
            }
        });
    }

    @Transactional
    public void updateBuzzerState(String id, boolean buzzerOn) {
        deviceRepository.findById(id).ifPresent(device -> {
            device.setBuzzerOn(buzzerOn);
            deviceRepository.save(device);
            realtimePublisher.publishState(id, toDto(device));
        });
    }

    public DeviceStateDto toDto(Device d) {
        DeviceStateDto dto = new DeviceStateDto();
        dto.setId(d.getId());
        dto.setName(d.getName());
        dto.setZone(d.getZone());
        dto.setOnline(d.isOnline());
        dto.setWorn(d.isWorn());
        dto.setBuzzerOn(d.isBuzzerOn());
        dto.setLastSeenAt(d.getLastSeenAt());
        dto.setLastLat(d.getLastLat());
        dto.setLastLng(d.getLastLng());
        dto.setCreatedAt(d.getCreatedAt());
        return dto;
    }
}
