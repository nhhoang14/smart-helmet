package com.helmet.controller;

import com.helmet.dto.AlertDto;
import com.helmet.dto.DeviceStateDto;
import com.helmet.model.Device;
import com.helmet.model.AlertStatus;
import com.helmet.model.Telemetry;
import com.helmet.model.ThresholdSettings;
import com.helmet.repository.AlertRepository;
import com.helmet.repository.DeviceRepository;
import com.helmet.repository.ThresholdSettingsRepository;
import com.helmet.service.AlertService;
import com.helmet.service.CommandService;
import com.helmet.service.DeviceService;
import com.helmet.service.TelemetryService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller Thymeleaf – phục vụ toàn bộ giao diện Web Dashboard.
 * Chạy HelmetApplication.java là ứng dụng khởi động, truy cập http://localhost:8080
 */
@Controller
public class DashboardController {

    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final AlertService alertService;
    private final DeviceService deviceService;
    private final TelemetryService telemetryService;
    private final CommandService commandService;
    private final ThresholdSettingsRepository thresholdSettingsRepository;

    public DashboardController(DeviceRepository deviceRepository,
                               AlertRepository alertRepository,
                               AlertService alertService,
                               DeviceService deviceService,
                               TelemetryService telemetryService,
                               CommandService commandService,
                               ThresholdSettingsRepository thresholdSettingsRepository) {
        this.deviceRepository = deviceRepository;
        this.alertRepository = alertRepository;
        this.alertService = alertService;
        this.deviceService = deviceService;
        this.telemetryService = telemetryService;
        this.commandService = commandService;
        this.thresholdSettingsRepository = thresholdSettingsRepository;
    }

    // ==========================================
    // TRANG DASHBOARD CHÍNH
    // ==========================================

    @GetMapping({"/", "/dashboard"})
    public String dashboard(
            @RequestParam(required = false) String deviceUid,
            Model model) {

        List<Device> devices = deviceRepository.findByDeletedAtIsNull();
        model.addAttribute("devices", devices);

        // Chọn thiết bị hiện tại (mặc định = thiết bị đầu tiên)
        String selectedUid = deviceUid;
        if (selectedUid == null && !devices.isEmpty()) {
            selectedUid = devices.get(0).getDeviceUid();
        }
        model.addAttribute("selectedUid", selectedUid);

        if (selectedUid != null) {
            DeviceStateDto state = deviceService.getDeviceStateDto(selectedUid);
            model.addAttribute("state", state);
            final String dashboardDeviceUid = selectedUid;
            model.addAttribute("threshold", thresholdSettingsRepository.findByDeviceUid(dashboardDeviceUid)
                    .orElseGet(() -> ThresholdSettings.builder().deviceUid(dashboardDeviceUid).build()));

            List<AlertDto> recentAlerts = alertService.getDeviceAlerts(selectedUid);
            int end = Math.min(20, recentAlerts.size());
            model.addAttribute("recentAlerts", recentAlerts.subList(0, end));
        }

        // Thống kê tổng
        long onlineCount = devices.stream().filter(Device::isOnline).count();
        long alertCount = alertRepository.countByStatus(AlertStatus.NEW);
        model.addAttribute("onlineCount", onlineCount);
        model.addAttribute("alertCount", alertCount);
        model.addAttribute("totalDevices", devices.size());

        model.addAttribute("activeTab", "dashboard");
        return "dashboard";
    }

    // ==========================================
    // TRANG QUẢN LÝ THIẾT BỊ
    // ==========================================

    @GetMapping("/devices")
    public String devices(Model model) {
        List<Device> devices = deviceRepository.findByDeletedAtIsNull();
        model.addAttribute("devices", devices);
        model.addAttribute("activeTab", "devices");
        return "devices";
    }

    @GetMapping("/devices/new")
    public String newDeviceForm(Model model) {
        model.addAttribute("activeTab", "devices");
        model.addAttribute("editMode", false);
        return "device-form";
    }

    @PostMapping("/devices/new")
    public String createDevice(
            @RequestParam String deviceUid,
            @RequestParam String name,
            @RequestParam(required = false) String workerName,
            Model model) {
        try {
            Device device = Device.builder()
                    .deviceUid(deviceUid)
                    .name(name)
                    .workerName(workerName)
                    .isOnline(false)
                    .isWorn(false)
                    .buzzerOn(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            deviceService.registerDevice(device);
            return "redirect:/devices?added=true";
        } catch (Exception e) {
            model.addAttribute("error", "Lỗi đăng ký thiết bị: " + e.getMessage());
            model.addAttribute("activeTab", "devices");
            return "device-form";
        }
    }

    @GetMapping("/devices/{uid}/edit")
    public String editDeviceForm(@PathVariable String uid, Model model) {
        return deviceService.getDeviceByUid(uid).map(device -> {
            model.addAttribute("device", device);
            model.addAttribute("editMode", true);
            model.addAttribute("activeTab", "devices");
            return "device-form";
        }).orElse("redirect:/devices");
    }

    @PostMapping("/devices/{uid}/edit")
    public String updateDevice(@PathVariable String uid,
                               @RequestParam String name,
                               @RequestParam(required = false) String workerName,
                               Model model) {
        try {
            Device updated = Device.builder().name(name).workerName(workerName).build();
            if (deviceService.updateDevice(uid, updated).isPresent()) {
                return "redirect:/devices";
            }
            return "redirect:/devices";
        } catch (IllegalArgumentException e) {
            model.addAttribute("device", Device.builder()
                    .deviceUid(uid).name(name).workerName(workerName).build());
            model.addAttribute("editMode", true);
            model.addAttribute("activeTab", "devices");
            model.addAttribute("error", "Không thể cập nhật thiết bị: " + e.getMessage());
            return "device-form";
        }
    }

    @PostMapping("/devices/{uid}/delete")
    public String deleteDevice(@PathVariable String uid) {
        deviceService.softDeleteDevice(uid);
        return "redirect:/devices";
    }

    @PostMapping("/devices/{uid}/buzzer")
    public String toggleBuzzer(
            @PathVariable String uid,
            @RequestParam boolean on) {
        Map<String, Object> payload = Map.of("state", on ? "ON" : "OFF");
        commandService.executeCommand(uid, "FIND_HELMET", payload);
        return "redirect:/dashboard?deviceUid=" + uid;
    }

    // ==========================================
    // TRANG LỊCH SỬ CẢNH BÁO
    // ==========================================

    @GetMapping("/alerts")
    public String alerts(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        int pageSize = 20;
        Page<AlertDto> alertPage = alertService.getAlerts(type, status, page, pageSize);
        model.addAttribute("alertPage", alertPage);
        model.addAttribute("alerts", alertPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", alertPage.getTotalPages());
        model.addAttribute("filterType", type);
        model.addAttribute("filterStatus", status);
        model.addAttribute("activeTab", "alerts");
        return "alerts";
    }

    @PostMapping("/alerts/{id}/ack")
    public String ackAlert(@PathVariable Long id,
                            @RequestParam(defaultValue = "/alerts") String redirectTo) {
        alertService.acknowledge(id, null);
        return "redirect:" + redirectTo;
    }

    // ==========================================
    // TRANG LỊCH SỬ DỮ LIỆU CẢM BIẾN
    // ==========================================

    @GetMapping("/history")
    public String history(
            @RequestParam(required = false) String deviceUid,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to,
            Model model) {

        List<Device> devices = deviceRepository.findByDeletedAtIsNull();
        model.addAttribute("devices", devices);

        String selectedUid = deviceUid;
        if (selectedUid == null && !devices.isEmpty()) {
            selectedUid = devices.get(0).getDeviceUid();
        }
        model.addAttribute("selectedUid", selectedUid);

        if (selectedUid != null) {
            Instant end = (to != null) ? Instant.ofEpochMilli(to) : Instant.now();
            Instant start = (from != null) ? Instant.ofEpochMilli(from) : end.minus(1, ChronoUnit.HOURS);

            List<Telemetry> history = telemetryService.getHistory(selectedUid, start, end);
            model.addAttribute("telemetry", history);
            model.addAttribute("fromMs", start.toEpochMilli());
            model.addAttribute("toMs", end.toEpochMilli());
        }

        model.addAttribute("activeTab", "history");
        return "history";
    }

    // ==========================================
    // TRANG CẤU HÌNH NGƯỠNG
    // ==========================================

    @GetMapping("/settings")
    public String settings(
            @RequestParam(required = false) String deviceUid,
            @RequestParam(required = false) String saved,
            Model model) {

        List<Device> devices = deviceRepository.findByDeletedAtIsNull();
        model.addAttribute("devices", devices);

        String selectedUid = deviceUid;
        if (selectedUid == null && !devices.isEmpty()) {
            selectedUid = devices.get(0).getDeviceUid();
        }
        model.addAttribute("selectedUid", selectedUid);

        if (selectedUid != null) {
            Optional<ThresholdSettings> threshold = thresholdSettingsRepository.findByDeviceUid(selectedUid);
            model.addAttribute("threshold", threshold.orElse(ThresholdSettings.builder()
                    .deviceUid(selectedUid)
                    .gasWarn(300.0)
                    .gasDanger(600.0)
                    .fallSensitivity(2.5)
                    .fallInactivitySec(15)
                    .isActive(true)
                    .build()));
        }

        if ("true".equals(saved)) {
            model.addAttribute("successMsg", "Đã lưu cấu hình ngưỡng thành công!");
        }

        model.addAttribute("activeTab", "settings");
        return "settings";
    }

    @PostMapping("/settings/{uid}")
    public String saveSettings(
            @PathVariable String uid,
            @RequestParam Double gasWarn,
            @RequestParam Double gasDanger,
            @RequestParam Double fallSensitivity,
            @RequestParam Integer fallInactivitySec) {

        Optional<ThresholdSettings> existing = thresholdSettingsRepository.findByDeviceUid(uid);
        ThresholdSettings settings = existing.orElse(ThresholdSettings.builder()
                .deviceUid(uid)
                .createdAt(Instant.now())
                .build());

        settings.setGasWarn(gasWarn);
        settings.setGasDanger(gasDanger);
        settings.setFallSensitivity(fallSensitivity);
        settings.setFallInactivitySec(fallInactivitySec);
        settings.setIsActive(true);
        settings.setUpdatedAt(Instant.now());

        thresholdSettingsRepository.save(settings);
        return "redirect:/settings?deviceUid=" + uid + "&saved=true";
    }
}
