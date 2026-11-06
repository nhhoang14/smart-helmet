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
    private final DeviceStateService deviceStateService;
    private final RealtimePublisher realtimePublisher;

    public DeviceService(DeviceRepository deviceRepository,
                         TelemetryRepository telemetryRepository,
                         DeviceStateService deviceStateService,
                         RealtimePublisher realtimePublisher) {
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
        this.deviceStateService = deviceStateService;
        this.realtimePublisher = realtimePublisher;
    }

    public List<Device> getAllDevices() {
        return deviceRepository.findByDeletedAtIsNull();
    }

    public Optional<Device> getDeviceByUid(String deviceUid) {
        return deviceRepository.findByDeviceUidAndDeletedAtIsNull(deviceUid);
    }

    public Optional<Device> getDevice(String idOrUid) {
        Optional<Device> byUid = deviceRepository.findByDeviceUidAndDeletedAtIsNull(idOrUid);
        if (byUid.isPresent()) return byUid;
        try {
            Long id = Long.parseLong(idOrUid);
            return deviceRepository.findById(id).filter(d -> d.getDeletedAt() == null);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public Device registerDevice(Device device) {
        String deviceUid = device.getDeviceUid() == null ? "" : device.getDeviceUid().trim();
        if (!deviceUid.matches("[A-Za-z0-9][A-Za-z0-9_-]{2,99}")) {
            throw new IllegalArgumentException("Mã thiết bị phải dài 3-100 ký tự, chỉ gồm chữ, số, '-' hoặc '_'.");
        }
        if (deviceRepository.existsByDeviceUid(deviceUid)) {
            throw new IllegalArgumentException("Thiết bị có mã " + deviceUid + " đã tồn tại!");
        }
        device.setDeviceUid(deviceUid);
        if (device.getName() == null || device.getName().isBlank()) {
            device.setName("Mũ " + deviceUid);
        }
        device.setCreatedAt(Instant.now());
        device.setUpdatedAt(Instant.now());
        Device saved = deviceRepository.save(device);

        // Khởi tạo trạng thái ban đầu trong cache Redis
        deviceStateService.updateDeviceState(deviceUid, s -> {
            s.setName(saved.getName());
            s.setOnline(false);
            s.setWorn(false);
            s.setBuzzerOn(false);
        });

        return saved;
    }

    @Transactional
    public Optional<Device> updateDevice(String deviceUid, Device updatedInfo) {
        return deviceRepository.findByDeviceUidAndDeletedAtIsNull(deviceUid).map(dev -> {
            if (updatedInfo.getName() != null) dev.setName(updatedInfo.getName());
            if (updatedInfo.getWorkerName() != null) dev.setWorkerName(updatedInfo.getWorkerName());
            dev.setUpdatedAt(Instant.now());
            Device saved = deviceRepository.save(dev);

            deviceStateService.updateDeviceState(deviceUid, s -> {
                s.setName(saved.getName());
            });
            return saved;
        });
    }

    @Transactional
    public boolean softDeleteDevice(String deviceUid) {
        return deviceRepository.findByDeviceUidAndDeletedAtIsNull(deviceUid).map(dev -> {
            dev.setDeletedAt(Instant.now());
            deviceRepository.save(dev);
            log.info("Soft-deleted device with UID: {}", deviceUid);
            return true;
        }).orElse(false);
    }

    /**
     * Lấy trạng thái tức thời của mũ từ Redis (Mục 3.5.2 & Mục 3.6: GET /api/v1/devices/{deviceUid}/state)
     */
    public DeviceStateDto getDeviceStateDto(String deviceUid) {
        // Đọc trực tiếp từ Redis / DeviceStateService
        DeviceStateDto dto = deviceStateService.getDeviceState(deviceUid);

        // Bổ sung thông tin từ DB nếu chưa có
        deviceRepository.findByDeviceUidAndDeletedAtIsNull(deviceUid).ifPresent(d -> {
            dto.setName(d.getName());
            dto.setWorkerName(d.getWorkerName());
            dto.setOnline(d.isOnline());
            dto.setWorn(d.isWorn());
            dto.setBuzzerOn(d.isBuzzerOn());
            if (d.getLastSeenAt() != null) dto.setLastSeenAt(d.getLastSeenAt());
            if (d.getLastLat() != null) dto.setLastLat(d.getLastLat());
            if (d.getLastLng() != null) dto.setLastLng(d.getLastLng());
        });

        // Lấy gasPpm gần nhất nếu cache chưa có
        if (dto.getGasPpm() == null || dto.getGasPpm() == 0.0) {
            telemetryRepository.findTop1ByDeviceUidOrderByLogTimeDesc(deviceUid)
                    .ifPresent(t -> {
                        dto.setGasPpm(t.getGasPpm());
                        dto.setAccMagnitude(t.getAccMagnitude());
                    });
        }

        return dto;
    }

    @Transactional
    public void updateOnlineStatus(String deviceUid, boolean isOnline) {
        deviceRepository.findByDeviceUidAndDeletedAtIsNull(deviceUid).ifPresent(device -> {
            boolean changed = device.isOnline() != isOnline;
            device.setOnline(isOnline);
            if (isOnline) {
                device.setLastSeenAt(Instant.now());
            }
            deviceRepository.save(device);

            // Cập nhật trạng thái Redis và bắn WebSocket
            DeviceStateDto state = deviceStateService.updateDeviceState(deviceUid, s -> {
                s.setOnline(isOnline);
                if (isOnline) s.setLastSeenAt(Instant.now());
            });

            if (changed) {
                realtimePublisher.publishState(deviceUid, state);
            }
        });
    }

    @Transactional
    public void updateBuzzerState(String deviceUid, boolean buzzerOn) {
        deviceRepository.findByDeviceUidAndDeletedAtIsNull(deviceUid).ifPresent(device -> {
            device.setBuzzerOn(buzzerOn);
            deviceRepository.save(device);

            DeviceStateDto state = deviceStateService.updateDeviceState(deviceUid, s -> {
                s.setBuzzerOn(buzzerOn);
            });
            realtimePublisher.publishState(deviceUid, state);
        });
    }
}
