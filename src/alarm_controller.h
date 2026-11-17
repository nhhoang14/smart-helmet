/**
 * @file alarm_controller.h
 * @brief Điều khiển còi cảnh báo không chặn (Non-blocking)
 */

#ifndef ALARM_CONTROLLER_H
#define ALARM_CONTROLLER_H

#include <Arduino.h>
#include "config.h"

class AlarmController {
public:
    explicit AlarmController(uint8_t buzzerPin);

    void begin();
    void setState(SystemState newState);
    void update();

    // Cho phép kích hoạt tiếng bíp ngắn để phản hồi (test còi)
    void triggerBeep(uint16_t durationMs = 100);

private:
    uint8_t _buzzerPin;
    SystemState _currentState;
    unsigned long _lastToggleTime;
    bool _toggleState;

    unsigned long _beepEndTime;
    bool _isBeepingOnce;

    void setBuzzer(bool active);
};

#endif // ALARM_CONTROLLER_H
