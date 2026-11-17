#pragma once
#include <Arduino.h>
struct HelmetSnapshot {
    float gasPpm = NAN, gasAdc = NAN, accelerationG = NAN, tiltDegrees = NAN;
    float gasVoltage = NAN;
    uint32_t gasWarmupSeconds = 0;
    bool gasReady = false, sensorOk = false, calibrated = false;
    bool worn = false, wornKnown = false, fall = false, buzzerOn = false;
    char fallState[24] = "MONITORING";
};
enum class HelmetCommandType : uint8_t { MUTE, FIND_ON, FIND_OFF, SET_THRESHOLDS };
struct HelmetCommand {
    HelmetCommandType type;
    float gasWarn = NAN, gasDanger = NAN, impactG = NAN;
};
void beginHelmetServices();
void updateHelmetSnapshot(const HelmetSnapshot& snapshot);
void queueHelmetEvent(const char* type, const char* message, float value = NAN);
bool takeHelmetCommand(HelmetCommand& command);
