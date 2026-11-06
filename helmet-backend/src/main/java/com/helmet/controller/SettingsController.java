package com.helmet.controller;

import com.helmet.dto.ApiResponse;
import com.helmet.dto.SettingsDto;
import com.helmet.dto.ThresholdSettingsDto;
import com.helmet.model.ThresholdSettings;
import com.helmet.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    // ==========================================
    // 1. REST API CHUẨN V1 (THEO BẢNG 3.13 YÊU CẦU.MD)
    // ==========================================

    @GetMapping("/api/v1/thresholds/{deviceUid}")
    public ApiResponse<ThresholdSettingsDto> getThresholdsV1(@PathVariable String deviceUid) {
        ThresholdSettings settings = settingsService.getThresholds(deviceUid);
        return ApiResponse.success(ThresholdSettingsDto.fromEntity(settings));
    }

    @PostMapping("/api/v1/thresholds")
    public ApiResponse<ThresholdSettingsDto> saveThresholdsV1(@RequestBody ThresholdSettingsDto dto) {
        if (dto.getDeviceUid() == null || dto.getDeviceUid().isBlank()) {
            return ApiResponse.error(400, "device_uid không được để trống");
        }
        ThresholdSettings updated = settingsService.updateThresholds(dto.getDeviceUid(), dto);
        return ApiResponse.success("Cập nhật cấu hình ngưỡng thành công", ThresholdSettingsDto.fromEntity(updated));
    }

    // ==========================================
    // 2. ENDPOINTS TƯƠNG THÍCH FRONTEND HIỆN TẠI
    // ==========================================

    @GetMapping("/api/devices/{id}/settings")
    public ResponseEntity<SettingsDto> getSettingsLegacy(@PathVariable String id) {
        ThresholdSettings settings = settingsService.getSettings(id);
        return ResponseEntity.ok(SettingsDto.fromEntity(settings));
    }

    @PutMapping("/api/devices/{id}/settings")
    public ResponseEntity<SettingsDto> updateSettingsLegacy(
            @PathVariable String id,
            @Valid @RequestBody SettingsDto dto) {
        ThresholdSettings updated = settingsService.updateSettings(id, dto);
        return ResponseEntity.ok(SettingsDto.fromEntity(updated));
    }
}
