package com.helmet.service;

import com.helmet.dto.DeviceStateDto;
import com.helmet.dto.TelemetryMessage;
import com.helmet.model.Device;
import com.helmet.model.Telemetry;
import com.helmet.model.ThresholdSettings;
import com.helmet.realtime.RealtimePublisher;
import com.helmet.repository.DeviceRepository;
import com.helmet.repository.TelemetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
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
    private final DeviceStateService deviceStateService;
    private final RealtimePublisher realtimePublisher;

    private final Map<String, Instant> lastSavedTimeMap = new ConcurrentHashMap<>();

    public TelemetryService(TelemetryRepository telemetryRepository,
            DeviceRepository deviceRepository,
            SettingsService settingsService,
            AlertService alertService,
            DeviceStateService deviceStateService,
            RealtimePublisher realtimePublisher) {
        this.telemetryRepository = telemetryRepository;
        this.deviceRepository = deviceRepository;
        this.settingsService = settingsService;
        this.alertService = alertService;
        this.deviceStateService = deviceStateService;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional
    public void processTelemetry(String deviceUid, TelemetryMessage msg) {
        Instant now = Instant.now();
        Instant recordedAt = msg.getTimestampEpoch() != null
                ? (msg.getTimestampEpoch() > 10000000000L
                        ? Instant.ofEpochMilli(msg.getTimestampEpoch())
                        : Instant.ofEpochSecond(msg.getTimestampEpoch()))
                : now;

        // Chỉ tiếp nhận dữ liệu từ thiết bị đã đăng ký bằng deviceUid duy nhất.
        Device device = deviceRepository.findByDeviceUidAndDeletedAtIsNull(deviceUid).orElse(null);
        if (device == null) {
            log.warn("Ignored telemetry from unregistered device '{}'.", deviceUid);
            return;
        }

        device.setOnline(true);
        device.setLastSeenAt(now);

        Boolean isWorn = msg.getWornStatus();
        Double gas = msg.getGasPpm();
        Double accMagnitude = msg.getAccMagnitude() != null ? msg.getAccMagnitude() : 1.0;
        Double lat = msg.getLatitude();
        Double lng = msg.getLongitude();
        Boolean gpsFix = msg.getGpsFix();

        if (isWorn != null)
            device.setWorn(isWorn);
        if (msg.getBuzzerOn() != null)
            device.setBuzzerOn(msg.getBuzzerOn());
        if (lat != null)
            device.setLastLat(lat);
        if (lng != null)
            device.setLastLng(lng);
        deviceRepository.save(device);

        // 2. Cập nhật trạng thái tức thời trên Redis (Mục 3.7.2 bước 5:
        // DeviceStateService)
        DeviceStateDto state = deviceStateService.updateDeviceState(deviceUid, s -> {
            s.setOnline(true);
            if (isWorn != null) s.setWorn(isWorn);
            s.setWornKnown(msg.getWornKnown());
            s.setGasReady(msg.getGasReady());
            s.setGasWarmupRemaining(msg.getGasWarmupRemaining());
            s.setFallState(msg.getFallState());
            if (msg.getBuzzerOn() != null) {
                s.setBuzzerOn(msg.getBuzzerOn());
                s.setBuzzerState(msg.getBuzzerOn() ? "ON" : "OFF");
            }
            s.setGasPpm(gas);
            s.setAccMagnitude(accMagnitude);
            if (lat != null)
                s.setLastLat(lat);
            if (lng != null)
                s.setLastLng(lng);
            s.setLastSeenAt(now);

            Map<String, Object> sensors = msg.getSensors() != null ? new HashMap<>(msg.getSensors()) : new HashMap<>();
            if (gas != null) sensors.put("gas_ppm", gas);
            sensors.put("acc_magnitude", accMagnitude);
            s.setSensors(sensors);

            Map<String, Object> gps = new HashMap<>();
            if (lat != null)
                gps.put("latitude", lat);
            if (lng != null)
                gps.put("longitude", lng);
            gps.put("fix", gpsFix);
            gps.put("source", msg.resolveLocationSource());
            s.setGps(gps);
        });

        if (msg.getAcknowledgedFallId() != null && !msg.getAcknowledgedFallId().isBlank()) {
            alertService.acknowledgeFallFromDevice(deviceUid, msg.getAcknowledgedFallId());
        }

        // 3. Server chỉ tiếp nhận trạng thái ngã từ MPU6050 và thông số PPM từ MQ-135.
        // Logic xem ngưỡng và báo còi do thiết bị ESP32 tự xử lý cục bộ.
        // Khi thiết bị phát hiện té ngã hoặc trong ngưỡng cảnh báo, server ghi nhận và cập nhật vị trí.
        // Firmware V2 sends one confirmed FALL on the alert topic. Its repeated
        // telemetry describes the current state and must not create new alerts.
        if (!msg.hasFirmwareFallDetection() && Boolean.TRUE.equals(msg.getFall())) {
            alertService.handleEventWithValue(deviceUid, "FALL", accMagnitude, lat, lng,
                    "Phát hiện sự cố té ngã từ cảm biến MPU6050!", msg.resolveLocationSource());
        }

        // 5. Ghi dữ liệu cảm biến vào MySQL (Mục 3.7.2 bước 6)
        ThresholdSettings settings = settingsService.getThresholds(deviceUid);
        Instant lastSaved = lastSavedTimeMap.getOrDefault(deviceUid, Instant.EPOCH);
        long elapsedSec = java.time.Duration.between(lastSaved, now).toSeconds();
        boolean isExceeded = gas != null && gas >= (settings.getGasWarn() != null ? settings.getGasWarn() : 300.0);

        int sampleSec = settings.getTelemetrySampleSec() != null ? settings.getTelemetrySampleSec() : 5;
        if (gas != null && (isExceeded || elapsedSec >= sampleSec)) {
            saveTelemetryRecord(device, gas, accMagnitude, isWorn, lat, lng, gpsFix, recordedAt,
                    Boolean.TRUE.equals(msg.getFall()));
            lastSavedTimeMap.put(deviceUid, now);
        }

        // 6. Phát dữ liệu và trạng thái mới qua WebSocket (Mục 3.7.2 bước 7).
        // State phải được phát ở mỗi mẫu để dashboard nhận alertLevel vừa đánh giá.
        realtimePublisher.publishTelemetry(deviceUid, msg);
        realtimePublisher.publishState(deviceUid, deviceStateService.getDeviceState(deviceUid));
    }

    private void saveTelemetryRecord(Device device, Double gas, Double acc, Boolean isWorn,
            Double lat, Double lng, Boolean gpsFix, Instant recordedAt, boolean fall) {
        Telemetry t = Telemetry.builder()
                .deviceId(device.getId())
                .deviceUid(device.getUid())
                .gasPpm(gas)
                .accMagnitude(acc)
                .isWorn(Boolean.TRUE.equals(isWorn))
                .latitude(lat)
                .longitude(lng)
                .gpsFix(Boolean.TRUE.equals(gpsFix))
                .fallState(fall)
                .logTime(recordedAt)
                .createdAt(Instant.now())
                .build();
        telemetryRepository.save(t);
    }

    public List<Telemetry> getHistory(String deviceUid, Instant from, Instant to) {
        return telemetryRepository.findByDeviceUidAndLogTimeBetweenOrderByLogTimeAsc(deviceUid, from, to);
    }

    public List<Telemetry> getRecentSamples(String deviceUid) {
        return telemetryRepository.findTop60ByDeviceUidOrderByLogTimeDesc(deviceUid);
    }
}
