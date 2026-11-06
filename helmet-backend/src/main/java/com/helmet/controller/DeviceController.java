package com.helmet.controller;

import com.helmet.dto.AlertDto;
import com.helmet.dto.ApiResponse;
import com.helmet.dto.CommandRequest;
import com.helmet.dto.DeviceStateDto;
import com.helmet.model.Device;
import com.helmet.model.Telemetry;
import com.helmet.service.AlertService;
import com.helmet.service.CommandService;
import com.helmet.service.DeviceService;
import com.helmet.service.TelemetryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class DeviceController {

    private final DeviceService deviceService;
    private final TelemetryService telemetryService;
    private final AlertService alertService;
    private final CommandService commandService;

    public DeviceController(DeviceService deviceService,
                            TelemetryService telemetryService,
                            AlertService alertService,
                            CommandService commandService) {
        this.deviceService = deviceService;
        this.telemetryService = telemetryService;
        this.alertService = alertService;
        this.commandService = commandService;
    }

    // ==========================================
    // 1. REST API CHUẨN V1 (THEO BẢNG 3.13 YÊU CẦU.MD)
    // ==========================================

    @GetMapping("/api/v1/devices")
    public ApiResponse<List<Device>> getAllDevicesV1() {
        return ApiResponse.success(deviceService.getAllDevices());
    }

    @PostMapping("/api/v1/devices")
    public ApiResponse<Device> registerDeviceV1(@RequestBody Device device) {
        try {
            Device saved = deviceService.registerDevice(device);
            return ApiResponse.success("Đăng ký mũ bảo hộ thành công", saved);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/api/v1/devices/{deviceUid}")
    public ApiResponse<Device> updateDeviceV1(@PathVariable String deviceUid, @RequestBody Device updated) {
        return deviceService.updateDevice(deviceUid, updated)
                .map(d -> ApiResponse.success("Cập nhật thông tin thành công", d))
                .orElse(ApiResponse.error(404, "Không tìm thấy thiết bị: " + deviceUid));
    }

    @DeleteMapping("/api/v1/devices/{deviceUid}")
    public ApiResponse<Boolean> deleteDeviceV1(@PathVariable String deviceUid) {
        boolean ok = deviceService.softDeleteDevice(deviceUid);
        if (ok) {
            return ApiResponse.success("Đã xóa mềm thiết bị thành công", true);
        }
        return ApiResponse.error(404, "Không tìm thấy thiết bị: " + deviceUid);
    }

    @GetMapping("/api/v1/devices/{deviceUid}/state")
    public ApiResponse<DeviceStateDto> getDeviceStateV1(@PathVariable String deviceUid) {
        DeviceStateDto state = deviceService.getDeviceStateDto(deviceUid);
        return ApiResponse.success(state);
    }

    @GetMapping("/api/v1/devices/{deviceUid}/history")
    public ApiResponse<List<Telemetry>> getDeviceHistoryV1(
            @PathVariable String deviceUid,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {

        Instant end = (to != null) ? Instant.ofEpochMilli(to) : Instant.now();
        Instant start = (from != null) ? Instant.ofEpochMilli(from) : end.minus(24, ChronoUnit.HOURS);

        List<Telemetry> list = telemetryService.getHistory(deviceUid, start, end);
        return ApiResponse.success(list);
    }

    @GetMapping("/api/v1/devices/{deviceUid}/alerts")
    public ApiResponse<List<AlertDto>> getDeviceAlertsV1(@PathVariable String deviceUid) {
        List<AlertDto> alerts = alertService.getDeviceAlerts(deviceUid);
        return ApiResponse.success(alerts);
    }

    @PostMapping("/api/v1/devices/{deviceUid}/command")
    public ApiResponse<Boolean> sendCommandV1(
            @PathVariable String deviceUid,
            @RequestBody CommandRequest request) {
        String action = request.getAction() != null ? request.getAction() : "FIND_HELMET";
        Map<String, Object> payload = request.getPayload();
        if (payload == null && request.getOn() != null) {
            payload = Map.of("state", request.getOn() ? "ON" : "OFF");
        }
        boolean ok = commandService.executeCommand(deviceUid, action, payload);
        return ApiResponse.success("Đã gửi lệnh xuống mũ", ok);
    }

    // ==========================================
    // 2. ENDPOINTS TƯƠNG THÍCH FRONTEND HIỆN TẠI (/api/devices)
    // ==========================================

    @GetMapping("/api/devices")
    public List<Device> getAllDevicesLegacy() {
        return deviceService.getAllDevices();
    }

    @GetMapping("/api/devices/{id}")
    public ResponseEntity<DeviceStateDto> getDeviceLegacy(@PathVariable String id) {
        DeviceStateDto dto = deviceService.getDeviceStateDto(id);
        return ResponseEntity.ok(dto);
    }
}
