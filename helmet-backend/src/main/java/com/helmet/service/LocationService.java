package com.helmet.service;

import com.helmet.dto.DeviceStateDto;
import com.helmet.dto.LocationMessage;
import com.helmet.realtime.RealtimePublisher;
import com.helmet.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Nhận khu vực (zone) của mũ, lưu vào trạng thái tức thời và đẩy lên dashboard qua WebSocket.
 * Zone nằm trong DeviceStateDto nên mọi bản state phát sau đó (kể cả từ telemetry) đều mang theo.
 */
@Service
public class LocationService {
    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final DeviceRepository deviceRepository;
    private final DeviceStateService deviceStateService;
    private final RealtimePublisher realtimePublisher;

    public LocationService(DeviceRepository deviceRepository,
                           DeviceStateService deviceStateService,
                           RealtimePublisher realtimePublisher) {
        this.deviceRepository = deviceRepository;
        this.deviceStateService = deviceStateService;
        this.realtimePublisher = realtimePublisher;
    }

    /** @return false nếu thiết bị chưa được đăng ký. */
    public boolean processLocation(String deviceUid, LocationMessage msg) {
        if (deviceRepository.findByDeviceUidAndDeletedAtIsNull(deviceUid).isEmpty()) {
            log.warn("Ignored location from unregistered device '{}'.", deviceUid);
            return false;
        }

        String zone = msg.resolveZone();
        long now = System.currentTimeMillis();

        DeviceStateDto state = deviceStateService.updateDeviceState(deviceUid, s -> {
            if (!zone.equals(s.getZone())) {
                log.info("Device {} zone: {} -> {}", deviceUid, s.getZone(), zone);
            }
            s.setZone(zone);
            s.setZoneSource(msg.resolveSource());
            s.setZoneRssi(msg.getRssi());
            s.setZoneConfidence(msg.getConfidence());
            s.setZoneUpdatedAt(now);
        });

        realtimePublisher.publishState(deviceUid, state);
        return true;
    }
}
