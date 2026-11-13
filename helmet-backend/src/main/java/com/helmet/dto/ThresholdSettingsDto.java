package com.helmet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.helmet.model.ThresholdSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdSettingsDto {

    @JsonProperty("device_uid")
    private String deviceUid;

    @JsonProperty("gas_warn")
    private Double gasWarn;

    @JsonProperty("gas_danger")
    private Double gasDanger;

    /**
     * Ngưỡng gia tốc va chạm để phát hiện té ngã (ví dụ 2.5g ~ 3.0g).
     * Thay cho TinyML, ngưỡng gia tốc này được dùng để kích hoạt các pha xét té ngã.
     */
    @JsonProperty("fall_sensitivity")
    private Double fallSensitivity;

    @JsonProperty("fall_inactivity_sec")
    private Integer fallInactivitySec;

    @JsonProperty("is_active")
    private Boolean isActive;

    public static ThresholdSettingsDto fromEntity(ThresholdSettings entity) {
        if (entity == null) return null;
        return ThresholdSettingsDto.builder()
                .deviceUid(entity.getDeviceUid())
                .gasWarn(entity.getGasWarn())
                .gasDanger(entity.getGasDanger())
                .fallSensitivity(entity.getFallSensitivity())
                .fallInactivitySec(entity.getFallInactivitySec())
                .isActive(entity.getIsActive())
                .build();
    }
}

