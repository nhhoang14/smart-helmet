package com.helmet.service;

import com.helmet.dto.TelemetryMessage;
import com.helmet.model.Device;
import com.helmet.model.DeviceSettings;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TelemetryService {
    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final TelemetryRepository telemetryRepository;
    private final DeviceRepository deviceRepository;
    private final SettingsService settingsService;
    private final AlertService alertService;
    private final DeviceService deviceService;
    private final RealtimePublisher realtimePublisher;

    // Track last saved timestamp per device to respect telemetry_sample_sec
    private final Map<String, Instant> lastSavedTimeMap = new ConcurrentHashMap<>();

    public TelemetryService(TelemetryRepository telemetryRepository,
                            DeviceRepository deviceRepository,
                            SettingsService settingsService,
                            AlertService alertService,
                            DeviceService deviceService,
                            RealtimePublisher realtimePublisher) {
        this.telemetryRepository = telemetryRepository;
        this.deviceRepository = deviceRepository;
        this.settingsService = settingsService;
        this.alertService = alertService;
        this.deviceService = deviceService;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional
    public void processTelemetry(String deviceId, TelemetryMessage msg) {
        Instant now = Instant.now();
        Instant recordedAt = msg.getTs() != null ? Instant.ofEpochSecond(msg.getTs()) : now;

        Device device = deviceRepository.findById(deviceId)
                .orElseGet(() -> deviceRepository.save(Device.builder()
                        .id(deviceId)
                        .name("Mũ #" + deviceId)
                        .zone("Khu vực hầm lò")
                        .build()));

        boolean wasOffline = !device.isOnline();
        device.setOnline(true);
        device.setLastSeenAt(now);
        if (msg.getWorn() != null) device.setWorn(msg.getWorn());
        if (msg.getLat() != null) device.setLastLat(msg.getLat());
        if (msg.getLng() != null) device.setLastLng(msg.getLng());
        deviceRepository.save(device);

        DeviceSettings settings = settingsService.getSettings(deviceId);
        Double gas = msg.getGas() != null ? msg.getGas() : 0.0;

        // Đánh giá cảnh báo khí
        alertService.evaluateGas(device, gas, device.getLastLat(), device.getLastLng());

        // Đánh giá sự kiện té ngã nếu có cờ fall
        if (Boolean.TRUE.equals(msg.getFall())) {
            alertService.handleEvent(deviceId, "FALL", device.getLastLat(), device.getLastLng());
        }

        // Quyết định lưu telemetry vào DB:
        // Luôn lưu nếu khí vượt ngưỡng cảnh báo, hoặc đã qua chu kỳ lấy mẫu
        Instant lastSaved = lastSavedTimeMap.getOrDefault(deviceId, Instant.EPOCH);
        long elapsedSec = java.time.Duration.between(lastSaved, now).toSeconds();
        boolean isExceeded = gas >= settings.getGasWarnPpm();

        if (isExceeded || elapsedSec >= settings.getTelemetrySampleSec()) {
            Telemetry t = Telemetry.builder()
                    .deviceId(deviceId)
                    .gasPpm(gas)
                    .isWorn(msg.getWorn())
                    .fallState(msg.getFall())
                    .lat(msg.getLat())
                    .lng(msg.getLng())
                    .recordedAt(recordedAt)
                    .build();
            telemetryRepository.save(t);
            lastSavedTimeMap.put(deviceId, now);
        }

        // Đẩy realtime xuống WebSocket
        realtimePublisher.publishTelemetry(deviceId, msg);

        // Nếu trạng thái thay đổi hoặc sau vài nhịp, đẩy state
        if (wasOffline) {
            realtimePublisher.publishState(deviceId, deviceService.getDeviceStateDto(deviceId));
        }
    }

    public List<Telemetry> getHistory(String deviceId, Instant from, Instant to) {
        return telemetryRepository.findByDeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc(deviceId, from, to);
    }

    public List<Telemetry> getRecentSamples(String deviceId) {
        return telemetryRepository.findTop60ByDeviceIdOrderByRecordedAtDesc(deviceId);
    }
}
