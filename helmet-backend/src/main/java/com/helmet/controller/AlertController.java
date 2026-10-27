package com.helmet.controller;

import com.helmet.dto.AlertDto;
import com.helmet.dto.EventMessage;
import com.helmet.service.AlertService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/alerts")
    public Page<AlertDto> getAlerts(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return alertService.getAlerts(type, status, page, size);
    }

    @PostMapping("/alerts/{id}/ack")
    public ResponseEntity<AlertDto> acknowledgeAlert(@PathVariable Long id) {
        return alertService.acknowledge(id, null)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/devices/{id}/event")
    public ResponseEntity<Void> ingestEvent(@PathVariable String id, @RequestBody EventMessage msg) {
        alertService.handleEvent(id, msg.getType(), msg.getLat(), msg.getLng());
        return ResponseEntity.ok().build();
    }
}
