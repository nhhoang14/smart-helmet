package com.helmet.controller;

import com.helmet.dto.WifiScanMessage;
import com.helmet.service.WifiFingerprintService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * API cho định vị Wi-Fi fingerprint.
 *  - POST /api/devices/{id}/wifiscan      gửi một lần quét (thử bằng tay; mũ thật gửi qua MQTT)
 *  - GET  /api/wifi-fingerprint/status    xem dữ liệu huấn luyện đã nạp
 *  - POST /api/wifi-fingerprint/reload    nạp lại file huấn luyện sau khi thu thêm dữ liệu
 */
@RestController
@CrossOrigin(origins = "*")
public class WifiFingerprintController {

    private final WifiFingerprintService wifiFingerprintService;

    public WifiFingerprintController(WifiFingerprintService wifiFingerprintService) {
        this.wifiFingerprintService = wifiFingerprintService;
    }

    @PostMapping("/api/devices/{id}/wifiscan")
    public ResponseEntity<Map<String, Object>> ingestScan(@PathVariable String id, @RequestBody WifiScanMessage msg) {
        WifiFingerprintService.Result r = wifiFingerprintService.processScan(id, msg);
        Map<String, Object> body = new HashMap<>();
        if (r == null) {
            body.put("success", false);
            body.put("message", "Chưa có dữ liệu huấn luyện hoặc bản quét rỗng");
            return ResponseEntity.ok(body);
        }
        body.put("success", true);
        body.put("zone", r.zone());
        body.put("raw_zone", r.rawZone());
        body.put("confidence", r.confidence());
        body.put("nearest_distance", r.nearestDistance());
        body.put("known_aps", r.knownAps());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/api/wifi-fingerprint/status")
    public Map<String, Object> status() {
        return wifiFingerprintService.status();
    }

    @PostMapping("/api/wifi-fingerprint/reload")
    public Map<String, Object> reload() {
        return wifiFingerprintService.reload();
    }
}
