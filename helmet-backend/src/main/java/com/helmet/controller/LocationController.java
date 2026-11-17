package com.helmet.controller;

import com.helmet.dto.DeviceStateDto;
import com.helmet.dto.LocationMessage;
import com.helmet.service.DeviceStateService;
import com.helmet.service.LocationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Cổng HTTP cho vị trí theo khu vực, dùng khi gửi thử bằng tay hoặc từ script Python.
 * Mũ thật gửi qua MQTT topic helmet/device/{uid}/location.
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/devices/{id}/location")
public class LocationController {

    private final LocationService locationService;
    private final DeviceStateService deviceStateService;

    public LocationController(LocationService locationService, DeviceStateService deviceStateService) {
        this.locationService = locationService;
        this.deviceStateService = deviceStateService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> updateLocation(@PathVariable String id,
                                                              @RequestBody LocationMessage msg) {
        Map<String, Object> body = new HashMap<>();
        boolean ok = locationService.processLocation(id, msg);
        body.put("success", ok);
        body.put("zone", msg.resolveZone());
        if (!ok) {
            body.put("message", "Thiết bị chưa đăng ký: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping
    public Map<String, Object> getLocation(@PathVariable String id) {
        DeviceStateDto s = deviceStateService.getDeviceState(id);
        Map<String, Object> body = new HashMap<>();
        body.put("device_uid", id);
        body.put("zone", s.getZone() != null ? s.getZone() : LocationMessage.UNKNOWN_ZONE);
        body.put("zone_source", s.getZoneSource());
        body.put("zone_rssi", s.getZoneRssi());
        body.put("zone_confidence", s.getZoneConfidence());
        body.put("zone_updated_at", s.getZoneUpdatedAt());
        return body;
    }
}
