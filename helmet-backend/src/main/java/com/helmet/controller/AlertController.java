package com.helmet.controller;

import com.helmet.dto.AlertDto;
import com.helmet.dto.ApiResponse;
import com.helmet.dto.EventMessage;
import com.helmet.service.AlertService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    // ==========================================
    // 1. REST API CHUẨN V1 (THEO BẢNG 3.13 YÊU CẦU.MD)
    // ==========================================

    @PostMapping("/api/v1/alerts/{alertId}/ack")
    public ApiResponse<AlertDto> acknowledgeAlertV1(@PathVariable Long alertId) {
        return alertService.acknowledge(alertId, null)
                .map(dto -> ApiResponse.success("Xác nhận sự cố thành công", dto))
                .orElse(ApiResponse.error(404, "Không tìm thấy sự cố với id: " + alertId));
    }

    // ==========================================
    // 2. ENDPOINTS TƯƠNG THÍCH FRONTEND HIỆN TẠI
    // ==========================================

    @GetMapping("/api/alerts")
    public Page<AlertDto> getAlerts(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return alertService.getAlerts(type, status, page, size);
    }

    @PostMapping("/api/alerts/{id}/ack")
    public ResponseEntity<AlertDto> acknowledgeAlertLegacy(@PathVariable Long id) {
        return alertService.acknowledge(id, null)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/devices/{id}/event")
    public ResponseEntity<Void> ingestEvent(@PathVariable String id, @RequestBody EventMessage msg) {
        alertService.handleEventWithValue(
                id,
                msg.resolveType(),
                msg.getValue(),
                msg.resolveLatitude(),
                msg.resolveLongitude()
        );
        return ResponseEntity.ok().build();
    }
}
