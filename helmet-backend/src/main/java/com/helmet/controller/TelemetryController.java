package com.helmet.controller;

import com.helmet.dto.TelemetryMessage;
import com.helmet.model.Telemetry;
import com.helmet.service.TelemetryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/devices/{id}/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @GetMapping
    public List<Telemetry> getTelemetry(
            @PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        Instant end = (to != null) ? to : Instant.now();
        Instant start = (from != null) ? from : end.minus(24, ChronoUnit.HOURS);

        return telemetryService.getHistory(id, start, end);
    }

    @PostMapping
    public ResponseEntity<Void> ingestTelemetry(@PathVariable String id, @RequestBody TelemetryMessage msg) {
        telemetryService.processTelemetry(id, msg);
        return ResponseEntity.ok().build();
    }
}
