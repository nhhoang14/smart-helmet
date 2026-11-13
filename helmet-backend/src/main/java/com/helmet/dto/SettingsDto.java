package com.helmet.dto;

import com.helmet.model.ThresholdSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class SettingsDto {

    @Min(50)
    @Max(2000)
    private int gasWarnPpm = 300;

    @Min(1)
    private double gasDangerFactor = 2.0;

    private Double gasDangerPpm = 600.0;

    /**
     * Ngưỡng gia tốc va chạm để phát hiện ngã (thay vì TinyML)
     */
    private Double fallSensitivity = 2.5;

    @Min(5)
    @Max(120)
    private int offlineTimeoutSec = 10;

    @Min(1)
    @Max(60)
    private int telemetrySampleSec = 5;

    public SettingsDto() {}

    public static SettingsDto fromEntity(ThresholdSettings s) {
        SettingsDto dto = new SettingsDto();
        dto.setGasWarnPpm(s.getGasWarn() != null ? s.getGasWarn().intValue() : 300);
        dto.setGasDangerPpm(s.getGasDanger() != null ? s.getGasDanger() : 600.0);
        if (s.getGasWarn() != null && s.getGasWarn() > 0 && s.getGasDanger() != null) {
            dto.setGasDangerFactor(s.getGasDanger() / s.getGasWarn());
        }
        dto.setFallSensitivity(s.getFallSensitivity());
        dto.setOfflineTimeoutSec(s.getOfflineTimeoutSec() != null ? s.getOfflineTimeoutSec() : 10);
        dto.setTelemetrySampleSec(s.getTelemetrySampleSec() != null ? s.getTelemetrySampleSec() : 5);
        return dto;
    }

    public int getGasWarnPpm() { return gasWarnPpm; }
    public void setGasWarnPpm(int gasWarnPpm) { this.gasWarnPpm = gasWarnPpm; }

    public double getGasDangerFactor() { return gasDangerFactor; }
    public void setGasDangerFactor(double gasDangerFactor) { this.gasDangerFactor = gasDangerFactor; }

    public Double getGasDangerPpm() { return gasDangerPpm; }
    public void setGasDangerPpm(Double gasDangerPpm) { this.gasDangerPpm = gasDangerPpm; }

    public Double getFallSensitivity() { return fallSensitivity; }
    public void setFallSensitivity(Double fallSensitivity) { this.fallSensitivity = fallSensitivity; }

    public int getOfflineTimeoutSec() { return offlineTimeoutSec; }
    public void setOfflineTimeoutSec(int offlineTimeoutSec) { this.offlineTimeoutSec = offlineTimeoutSec; }

    public int getTelemetrySampleSec() { return telemetrySampleSec; }
    public void setTelemetrySampleSec(int telemetrySampleSec) { this.telemetrySampleSec = telemetrySampleSec; }
}
