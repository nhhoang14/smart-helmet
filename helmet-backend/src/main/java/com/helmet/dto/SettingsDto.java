package com.helmet.dto;

import com.helmet.model.DeviceSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class SettingsDto {

    @Min(100)
    @Max(1000)
    private int gasWarnPpm = 400;

    @Min(1)
    private double gasDangerFactor = 1.5;

    @Min(5)
    @Max(120)
    private int offlineTimeoutSec = 10;

    @Min(1)
    @Max(60)
    private int telemetrySampleSec = 5;

    private String telegramChatId;

    public SettingsDto() {}

    public static SettingsDto fromEntity(DeviceSettings s) {
        SettingsDto dto = new SettingsDto();
        dto.setGasWarnPpm(s.getGasWarnPpm());
        dto.setGasDangerFactor(s.getGasDangerFactor());
        dto.setOfflineTimeoutSec(s.getOfflineTimeoutSec());
        dto.setTelemetrySampleSec(s.getTelemetrySampleSec());
        dto.setTelegramChatId(s.getTelegramChatId());
        return dto;
    }

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
}
