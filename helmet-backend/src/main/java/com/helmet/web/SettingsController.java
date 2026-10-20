package com.helmet.web;

import com.helmet.dto.SettingsDto;
import com.helmet.model.DeviceSettings;
import com.helmet.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices/{id}/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ResponseEntity<SettingsDto> getSettings(@PathVariable String id) {
        DeviceSettings settings = settingsService.getSettings(id);
        return ResponseEntity.ok(SettingsDto.fromEntity(settings));
    }

    @PutMapping
    public ResponseEntity<SettingsDto> updateSettings(
            @PathVariable String id,
            @Valid @RequestBody SettingsDto dto) {
        DeviceSettings updated = settingsService.updateSettings(id, dto);
        return ResponseEntity.ok(SettingsDto.fromEntity(updated));
    }
}
