package com.helmet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmet.dto.TelemetryMessage;
import com.helmet.model.Device;
import com.helmet.model.ThresholdSettings;
import com.helmet.realtime.RealtimePublisher;
import com.helmet.repository.DeviceRepository;
import com.helmet.repository.TelemetryRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirmwareCompatibilityTest {
    final TelemetryRepository records = mock(TelemetryRepository.class);
    final DeviceRepository devices = mock(DeviceRepository.class);
    final SettingsService settings = mock(SettingsService.class);
    final AlertService alerts = mock(AlertService.class);
    final DeviceStateService states = new DeviceStateService();
    final RealtimePublisher realtime = mock(RealtimePublisher.class);
    final TelemetryService service = new TelemetryService(records, devices, settings, alerts, states, realtime);
    final ObjectMapper json = new ObjectMapper();
    Device prepare() {
        Device device = Device.builder().id(1L).deviceUid("HELMET_001").isWorn(true).build();
        when(devices.findByDeviceUidAndDeletedAtIsNull("HELMET_001")).thenReturn(Optional.of(device));
        when(settings.getThresholds("HELMET_001")).thenReturn(ThresholdSettings.builder().build());
        return device;
    }
    @Test void firmwareFallStateDoesNotDuplicateItsExplicitAlert() throws Exception {
        prepare();
        service.processTelemetry("HELMET_001", json.readValue(
                "{\"fall_detection\":\"firmware_v2\",\"fall\":true,\"worn\":true,\"gas\":315,\"acc_magnitude\":3.5}", TelemetryMessage.class));
        verifyNoInteractions(alerts);
        verify(records).save(argThat(record -> Boolean.TRUE.equals(record.getFallState())));
    }
    @Test void absentWearSensorPreservesLastKnownDeviceStatus() throws Exception {
        Device device = prepare();
        TelemetryMessage message = json.readValue("{\"fall_detection\":\"firmware_v2\",\"worn_known\":false}", TelemetryMessage.class);
        assertNull(message.getWornStatus());
        service.processTelemetry("HELMET_001", message);
        assertTrue(device.isWorn());
        verifyNoInteractions(alerts);
    }
    @Test void switchOnAndOffPersistAndReachTheDashboard() throws Exception {
        Device device = prepare();
        for (boolean worn : new boolean[]{true, false}) {
            service.processTelemetry("HELMET_001", json.readValue(
                    "{\"worn_known\":true,\"worn\":" + worn + ",\"gas\":165,\"gas_ready\":true}", TelemetryMessage.class));
            assertEquals(worn, device.isWorn());
            assertEquals(worn, states.getDeviceState("HELMET_001").isWorn());
            assertEquals(165.0, states.getDeviceState("HELMET_001").getGasPpm());
        }
        verify(realtime, times(2)).publishState(eq("HELMET_001"), any());
    }
    @Test void warmupDoesNotInventSafeGasReadings() throws Exception {
        prepare();
        service.processTelemetry("HELMET_001", json.readValue(
                "{\"worn_known\":true,\"worn\":false,\"gas_ready\":false,\"gas_warmup_remaining\":42}", TelemetryMessage.class));
        assertNull(states.getDeviceState("HELMET_001").getGasPpm());
        assertEquals(42, states.getDeviceState("HELMET_001").getGasWarmupRemaining());
        verifyNoInteractions(records);
    }
    @Test void telemetryRetriesTheSpecificAcknowledgedFall() throws Exception {
        prepare();
        service.processTelemetry("HELMET_001", json.readValue(
                "{\"fall_detection\":\"firmware_v2\",\"acknowledged_fall_id\":\"boot-1\"}", TelemetryMessage.class));
        verify(alerts).acknowledgeFallFromDevice("HELMET_001", "boot-1");
    }
}
