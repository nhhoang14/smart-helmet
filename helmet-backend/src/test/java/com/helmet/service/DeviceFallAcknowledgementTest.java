package com.helmet.service;

import com.helmet.dto.AlertDto;
import com.helmet.model.*;
import com.helmet.mqtt.MqttCommandPublisher;
import com.helmet.realtime.RealtimePublisher;
import com.helmet.repository.AlertRepository;
import com.helmet.repository.DeviceRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceFallAcknowledgementTest {
    final AlertRepository alerts = mock(AlertRepository.class);
    final DeviceStateService states = new DeviceStateService();
    final RealtimePublisher realtime = mock(RealtimePublisher.class);
    final MqttCommandPublisher commands = mock(MqttCommandPublisher.class);
    final AlertService service = new AlertService(alerts, mock(DeviceRepository.class),
            mock(SettingsService.class), states, realtime, commands);

    Alert fall(String episode) {
        Alert alert = Alert.builder().id(7L).deviceUid("HELMET_001").type(AlertType.FALL)
                .severity(AlertSeverity.DANGER).sourceEventId(episode).build();
        when(alerts.findByDeviceUidAndStatusAndType("HELMET_001", AlertStatus.NEW, AlertType.FALL))
                .thenReturn(List.of(alert));
        when(alerts.save(any())).thenAnswer(call -> call.getArgument(0));
        states.addActiveAlert("HELMET_001", AlertDto.fromEntity(alert));
        return alert;
    }
    @Test void buttonAcknowledgesHistoryAndRestoresNormalWithoutMuteFeedback() {
        Alert alert = fall("boot-1");
        service.acknowledgeFallFromDevice("HELMET_001", "boot-1");
        assertEquals(AlertStatus.ACKNOWLEDGED, alert.getStatus());
        assertNotNull(alert.getAcknowledgedAt());
        assertEquals("NORMAL", states.getDeviceState("HELMET_001").getAlertLevel());
        verify(realtime).publishAlert(argThat(dto -> dto.getStatus() == AlertStatus.ACKNOWLEDGED));
        verifyNoInteractions(commands);
    }
    @Test void oldAcknowledgementCannotClearANewerFall() {
        Alert alert = fall("boot-2");
        service.acknowledgeFallFromDevice("HELMET_001", "boot-1");
        assertEquals(AlertStatus.NEW, alert.getStatus());
        assertEquals("DANGER", states.getDeviceState("HELMET_001").getAlertLevel());
        verify(alerts, never()).save(any());
    }
    @Test void fallAcknowledgementPreservesAnIndependentGasDanger() {
        fall("boot-1");
        states.addActiveAlert("HELMET_001", AlertDto.fromEntity(Alert.builder().id(8L)
                .deviceUid("HELMET_001").type(AlertType.GAS_DANGER).severity(AlertSeverity.DANGER).build()));
        service.acknowledgeFallFromDevice("HELMET_001", "boot-1");
        assertEquals("DANGER", states.getDeviceState("HELMET_001").getAlertLevel());
        assertTrue(states.getActiveAlerts("HELMET_001").containsKey(AlertType.GAS_DANGER));
    }
}
