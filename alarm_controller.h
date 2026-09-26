/**
 * @file alarm_controller.h
 * @brief Điều khiển còi và hệ thống LED cảnh báo không chặn (Non-blocking)
 */

#ifndef ALARM_CONTROLLER_H
#define ALARM_CONTROLLER_H

#include <Arduino.h>
#include "config.h"

class AlarmController {
public:
    AlarmController(uint8_t buzzerPin, uint8_t greenLedPin, uint8_t yellowLedPin, uint8_t redLedPin);

    void begin();
    void setState(SystemState newState);
    void update();

    // Cho phép kích hoạt tiếng bíp ngắn để phản hồi (test còi)
    void triggerBeep(uint16_t durationMs = 100);

private:
    uint8_t _buzzerPin;
    uint8_t _greenLedPin;
    uint8_t _yellowLedPin;
    uint8_t _redLedPin;

    SystemState _currentState;
    unsigned long _lastToggleTime;
    bool _toggleState;

    unsigned long _beepEndTime;
    bool _isBeepingOnce;

    void setLeds(bool green, bool yellow, bool red);
    void setBuzzer(bool active);
};

#endif // ALARM_CONTROLLER_H
