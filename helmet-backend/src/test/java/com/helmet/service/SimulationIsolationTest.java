package com.helmet.service;

import com.helmet.dto.TelemetryMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimulationIsolationTest {
    final TelemetryService telemetry = mock(TelemetryService.class);
    final AlertService alerts = mock(AlertService.class);
    final TelemetrySimulator simulator = new TelemetrySimulator(telemetry, alerts);
    SimulationIsolationTest() {
        ReflectionTestUtils.setField(simulator, "enabled", true);
        ReflectionTestUtils.setField(simulator, "simulationDeviceId", "HELMET_002");
    }
    @Test void measurementsAreOnlyWrittenToHelmetTwoAndMarkedSimulated() {
        simulator.generateSimulatedTelemetry();
        ArgumentCaptor<TelemetryMessage> packet = ArgumentCaptor.forClass(TelemetryMessage.class);
        verify(telemetry).processTelemetry(eq("HELMET_002"), packet.capture());
        assertTrue(packet.getValue().getSimulation());
        assertEquals("simulation", packet.getValue().resolveLocationSource());
        assertFalse(packet.getValue().getGpsFix());
        verifyNoInteractions(alerts);
    }
    @Test void configuringSimulatorForRealHelmetDoesNotWriteAnything() {
        ReflectionTestUtils.setField(simulator, "simulationDeviceId", "HELMET_001");
        simulator.generateSimulatedTelemetry();
        verifyNoInteractions(telemetry, alerts);
    }
    @Test void disabledSimulatorDoesNotWriteAnything() {
        ReflectionTestUtils.setField(simulator, "enabled", false);
        simulator.generateSimulatedTelemetry();
        verifyNoInteractions(telemetry, alerts);
    }
    @Test void gasWarningEmitsOneEventOnEnteringTheLevel() {
        ReflectionTestUtils.setField(simulator, "stepCount", 39);
        simulator.generateSimulatedTelemetry();
        simulator.generateSimulatedTelemetry();
        verify(alerts, times(1)).handleEventWithValue(eq("HELMET_002"), eq("GAS_WARNING"),
                anyDouble(), anyDouble(), anyDouble(), anyString(), eq("simulation"));
    }
    @Test void simulatedFallEmitsOneExplicitEventForHelmetTwo() {
        ReflectionTestUtils.setField(simulator, "stepCount", 99);
        simulator.generateSimulatedTelemetry();
        simulator.generateSimulatedTelemetry();
        verify(alerts, times(1)).handleEventWithValue(eq("HELMET_002"), eq("FALL"),
                anyDouble(), anyDouble(), anyDouble(), anyString(), eq("simulation"));
        verify(telemetry, never()).processTelemetry(eq("HELMET_001"), any());
    }
}
