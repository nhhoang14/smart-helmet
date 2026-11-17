package com.helmet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmet.mqtt.MqttInboundHandler;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class MqttAcknowledgementRoutingTest {
    @Test void acknowledgementIsNotMistakenForANewFallAlert() {
        AlertService alerts = mock(AlertService.class);
        MqttInboundHandler handler = new MqttInboundHandler(mock(TelemetryService.class), alerts,
                mock(DeviceService.class), new DeviceStateService(), mock(SettingsService.class), new ObjectMapper());
        handler.handleMessage("helmet/device/HELMET_001/alert",
                "{\"type\":\"FALL_ACK\",\"related_fall_id\":\"boot-1\"}");
        verify(alerts).acknowledgeFallFromDevice("HELMET_001", "boot-1");
        verifyNoMoreInteractions(alerts);
    }
}
