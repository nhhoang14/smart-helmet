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
import org.springframework.scheduling.annotation.Async;
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

        boolean wasOffline = !device.isOnline();
        device.setOnline(true);
        device.setLastSeenAt(now);

        Boolean isWorn = msg.getWornStatus();
        Double gas = msg.getGasPpm() != null ? msg.getGasPpm() : 0.0;
        Double accMagnitude = msg.getAccMagnitude() != null ? msg.getAccMagnitude() : 1.0;
        Double lat = msg.getLatitude();
        Double lng = msg.getLongitude();
        Boolean gpsFix = msg.getGpsFix();

        if (isWorn != null) device.setWorn(isWorn);
        if (lat != null) device.setLastLat(lat);
        if (lng != null) device.setLastLng(lng);
        deviceRepository.save(device);

        // 2. Cập nhật trạng thái tức thời trên Redis (Mục 3.7.2 bước 5: DeviceStateService)
        DeviceStateDto state = deviceStateService.updateDeviceState(deviceUid, s -> {
            s.setOnline(true);
            s.setWorn(Boolean.TRUE.equals(isWorn));
            s.setGasPpm(gas);
            s.setAccMagnitude(accMagnitude);
            if (lat != null) s.setLastLat(lat);
            if (lng != null) s.setLastLng(lng);
            s.setLastSeenAt(now);

            Map<String, Object> sensors = new HashMap<>();
            sensors.put("gas_ppm", gas);
            sensors.put("acc_magnitude", accMagnitude);
            s.setSensors(sensors);

            Map<String, Object> gps = new HashMap<>();
            if (lat != null) gps.put("latitude", lat);
            if (lng != null) gps.put("longitude", lng);
            gps.put("fix", gpsFix);
            s.setGps(gps);
        });

        // 3. Đánh giá cảnh báo khí độc theo ngưỡng (Mục 3.7.3)
        alertService.evaluateGas(device, gas, lat, lng);

        // 4. Đánh giá té ngã theo NGƯỠNG (Mục 3.7.4 - dùng ngưỡng gia tốc MPU6050 & Touch Pin, không dùng TinyML)
        alertService.evaluateFallByThreshold(device, accMagnitude, Boolean.TRUE.equals(isWorn), lat, lng);

        if (Boolean.TRUE.equals(msg.getFall())) {
            alertService.handleEvent(deviceUid, "FALL", lat, lng);
        }

        // 5. Ghi dữ liệu cảm biến vào MySQL (Mục 3.7.2 bước 6)
        ThresholdSettings settings = settingsService.getThresholds(deviceUid);
        Instant lastSaved = lastSavedTimeMap.getOrDefault(deviceUid, Instant.EPOCH);
        long elapsedSec = java.time.Duration.between(lastSaved, now).toSeconds();
        boolean isExceeded = gas >= (settings.getGasWarn() != null ? settings.getGasWarn() : 300.0);

        int sampleSec = settings.getTelemetrySampleSec() != null ? settings.getTelemetrySampleSec() : 5;
        if (isExceeded || elapsedSec >= sampleSec) {
            saveTelemetryRecord(device, gas, accMagnitude, isWorn, lat, lng, gpsFix, recordedAt);
            lastSavedTimeMap.put(deviceUid, now);
        }

        // 6. Phát trạng thái mới qua WebSocket (Mục 3.7.2 bước 7)
        realtimePublisher.publishTelemetry(deviceUid, msg);

        if (wasOffline) {
            realtimePublisher.publishState(deviceUid, state);
        }
    }

    private void saveTelemetryRecord(Device device, Double gas, Double acc, Boolean isWorn,
                                    Double lat, Double lng, Boolean gpsFix, Instant recordedAt) {
        Telemetry t = Telemetry.builder()
                .deviceId(device.getId())
                .deviceUid(device.getUid())
                .gasPpm(gas)
                .accMagnitude(acc)
                .isWorn(Boolean.TRUE.equals(isWorn))
                .latitude(lat)
                .longitude(lng)
                .gpsFix(Boolean.TRUE.equals(gpsFix))
                .fallState(false)
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
