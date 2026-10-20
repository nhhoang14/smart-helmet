package com.helmet.web;

import com.helmet.dto.DeviceStateDto;
import com.helmet.model.Device;
import com.helmet.service.DeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public List<Device> getAllDevices() {
        return deviceService.getAllDevices();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceStateDto> getDevice(@PathVariable String id) {
        DeviceStateDto dto = deviceService.getDeviceStateDto(id);
        return ResponseEntity.ok(dto);
    }
}
