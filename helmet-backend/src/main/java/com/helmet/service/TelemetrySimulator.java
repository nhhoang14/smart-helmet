package com.helmet.service;

import com.helmet.dto.TelemetryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Component
public class TelemetrySimulator {
    private static final Logger log = LoggerFactory.getLogger(TelemetrySimulator.class);

    private final TelemetryService telemetryService;
    private final Random random = new Random();

    @Value("${helmet.simulation.enabled:true}")
    private boolean enabled;

    @Value("${helmet.default-device-id:HELMET_001}")
    private String defaultDeviceId;

    private double currentGas = 185.0;
    private int stepCount = 0;

    public TelemetrySimulator(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @Scheduled(fixedDelayString = "${helmet.simulation.interval-ms:2000}")
    public void generateSimulatedTelemetry() {
        if (!enabled) return;

        stepCount++;
        // Dao động nồng độ khí thực tế quanh ngưỡng 150 - 260 ppm (Mức AN TOÀN < 300 ppm)
        double delta = (random.nextDouble() - 0.48) * 15.0;
        currentGas = Math.max(50.0, Math.min(850.0, currentGas + delta));

        // MPU6050: Gia tốc trọng trường bình thường ~ 0.98g - 1.05g
        double accMag = 0.98 + (random.nextDouble() - 0.5) * 0.12;

        // Kịch bản kiểm thử tự động các trường hợp xét ngưỡng (không dùng TinyML):
        if (stepCount % 120 == 40) {
            currentGas = 350.0; // Vượt ngưỡng CẢNH BÁO (> 300 ppm)
            log.info("[Simulation] Tạo nồng độ khí vượt ngưỡng CẢNH BÁO: 350 ppm");
        } else if (stepCount % 120 == 70) {
            currentGas = 650.0; // Vượt ngưỡng NGUY HIỂM (> 600 ppm)
            log.info("[Simulation] Tạo nồng độ khí vượt ngưỡng NGUY HIỂM: 650 ppm");
        } else if (stepCount % 120 == 85) {
            currentGas = 180.0; // Khí hồi phục về bình thường (< 300 - 25 hysteresis)
            log.info("[Simulation] Nồng độ khí giảm về mức AN TOÀN");
        } else if (stepCount % 120 == 100) {
            // Giả lập va chạm té ngã: Gia tốc đỉnh đạt 2.95g (> ngưỡng fall_sensitivity 2.5g)
            accMag = 2.95;
            log.info("[Simulation] Giả lập va chạm té ngã vượt ngưỡng: 2.95g");
        }

        // Tọa độ công trường khu vực Hà Nội (20.9805, 105.7875 theo smart-helmet-spec.md)
        double lat = 20.9805 + (random.nextDouble() - 0.5) * 0.0003;
        double lng = 105.7875 + (random.nextDouble() - 0.5) * 0.0003;

        TelemetryMessage msg = new TelemetryMessage();
        msg.setGas(Math.round(currentGas * 10.0) / 10.0);
        msg.setWorn(true);
        msg.setFall(false);
        msg.setLat(lat);
        msg.setLng(lng);
        msg.setFlatAccMagnitude(Math.round(accMag * 100.0) / 100.0);
        msg.setTs(System.currentTimeMillis() / 1000);

        Map<String, Object> sensors = new HashMap<>();
        sensors.put("gas_ppm", Math.round(currentGas * 10.0) / 10.0);
        sensors.put("acc_magnitude", Math.round(accMag * 100.0) / 100.0);
        msg.setSensors(sensors);

        Map<String, Object> gps = new HashMap<>();
        gps.put("latitude", lat);
        gps.put("longitude", lng);
        gps.put("fix", true);
        msg.setGps(gps);
        msg.setIsWorn(true);
        msg.setTimestamp(System.currentTimeMillis());

        // Chỉ giả lập một thiết bị khi chưa có phần cứng thật.
        telemetryService.processTelemetry(defaultDeviceId, msg);
    }
}
