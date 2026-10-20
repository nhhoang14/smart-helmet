package com.helmet.service;

import com.helmet.dto.TelemetryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Random;

@Component
public class TelemetrySimulator {
    private static final Logger log = LoggerFactory.getLogger(TelemetrySimulator.class);

    private final TelemetryService telemetryService;
    private final Random random = new Random();

    @Value("${helmet.simulation.enabled:true}")
    private boolean enabled;

    @Value("${helmet.default-device-id:01}")
    private String defaultDeviceId;

    private double currentGas = 220.0;
    private int stepCount = 0;

    public TelemetrySimulator(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @Scheduled(fixedDelayString = "${helmet.simulation.interval-ms:2000}")
    public void generateSimulatedTelemetry() {
        if (!enabled) return;

        stepCount++;
        // Tạo dao động gas thực tế quanh ngưỡng 180 - 350 ppm
        double delta = (random.nextDouble() - 0.48) * 20.0;
        currentGas = Math.max(50.0, Math.min(850.0, currentGas + delta));

        // Mỗi 100 chu kỳ thử nghiệm một đột biến nhẹ để kiểm tra cảnh báo
        if (stepCount % 120 == 60) {
            currentGas = 430.0; // vượt cảnh báo 400
        } else if (stepCount % 120 == 90) {
            currentGas = 620.0; // vượt nguy hiểm 600
        } else if (stepCount % 120 == 100) {
            currentGas = 200.0; // hồi phục an toàn
        }

        // Tọa độ công trường khu vực Hà Nội
        double lat = 21.0285 + (random.nextDouble() - 0.5) * 0.0005;
        double lng = 105.8542 + (random.nextDouble() - 0.5) * 0.0005;

        TelemetryMessage msg = new TelemetryMessage();
        msg.setGas(Math.round(currentGas * 10.0) / 10.0);
        msg.setWorn(true);
        msg.setFall(false);
        msg.setLat(lat);
        msg.setLng(lng);
        msg.setTs(Instant.now().getEpochSecond());

        telemetryService.processTelemetry(defaultDeviceId, msg);
    }
}
