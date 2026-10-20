package com.helmet.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "device_settings")
public class DeviceSettings {

    @Id
    @Column(name = "device_id", length = 32)
    private String deviceId;

    @Column(name = "gas_warn_ppm", nullable = false)
    private int gasWarnPpm = 400;

    @Column(name = "gas_danger_factor", nullable = false)
    private double gasDangerFactor = 1.5;

    @Column(name = "offline_timeout_sec", nullable = false)
    private int offlineTimeoutSec = 10;

    @Column(name = "telemetry_sample_sec", nullable = false)
    private int telemetrySampleSec = 5;

    @Column(name = "telegram_chat_id", length = 64)
    private String telegramChatId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DeviceSettings() {}

    public DeviceSettings(String deviceId) {
        this.deviceId = deviceId;
        this.updatedAt = Instant.now();
    }

    public int dangerThreshold() {
        return (int) Math.round(gasWarnPpm * gasDangerFactor);
    }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public int getGasWarnPpm() { return gasWarnPpm; }
    public void setGasWarnPpm(int gasWarnPpm) { this.gasWarnPpm = gasWarnPpm; }

    public double getGasDangerFactor() { return gasDangerFactor; }
    public void setGasDangerFactor(double gasDangerFactor) { this.gasDangerFactor = gasDangerFactor; }

    public int getOfflineTimeoutSec() { return offlineTimeoutSec; }
    public void setOfflineTimeoutSec(int offlineTimeoutSec) { this.offlineTimeoutSec = offlineTimeoutSec; }

    public int getTelemetrySampleSec() { return telemetrySampleSec; }
    public void setTelemetrySampleSec(int telemetrySampleSec) { this.telemetrySampleSec = telemetrySampleSec; }

    public String getTelegramChatId() { return telegramChatId; }
    public void setTelegramChatId(String telegramChatId) { this.telegramChatId = telegramChatId; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
