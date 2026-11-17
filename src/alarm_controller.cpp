/**
 * @file alarm_controller.cpp
 * @brief Hiện thực hệ thống cảnh báo chỉ bằng còi
 */

#include "alarm_controller.h"

AlarmController::AlarmController(uint8_t buzzerPin)
    : _buzzerPin(buzzerPin),
      _currentState(STATE_WARMING_UP),
      _lastToggleTime(0),
      _toggleState(false),
      _beepEndTime(0),
      _isBeepingOnce(false) {}

void AlarmController::begin() {
    pinMode(_buzzerPin, OUTPUT);

    setBuzzer(false);
}

void AlarmController::setState(SystemState newState) {
    if (_currentState != newState) {
        _currentState = newState;
        _lastToggleTime = millis();
        _toggleState = false;

        // Reset trạng thái tức thì khi đổi level
        if (_currentState == STATE_SAFE) {
            setBuzzer(false);
        } else if (_currentState == STATE_WARNING) {
            setBuzzer(false);
        } else if (_currentState == STATE_DANGER) {
            setBuzzer(true);
        }
    }
}

void AlarmController::triggerBeep(uint16_t durationMs) {
    _isBeepingOnce = true;
    _beepEndTime = millis() + durationMs;
    setBuzzer(true);
}

void AlarmController::update() {
    unsigned long now = millis();

    // Xử lý tiếng bíp đơn (nếu có yêu cầu từ hàm triggerBeep)
    if (_isBeepingOnce) {
        if (now >= _beepEndTime) {
            _isBeepingOnce = false;
            // Nếu không phải đang trong trạng thái DANGER thì tắt còi
            if (_currentState != STATE_DANGER) {
                setBuzzer(false);
            }
        }
        return; // Đang ưu tiên beep đơn
    }

    switch (_currentState) {
        case STATE_WARMING_UP: {
            if (now - _lastToggleTime >= 500) {
                _lastToggleTime = now;
                _toggleState = !_toggleState;
            }
            setBuzzer(false);
            break;
        }

        case STATE_SAFE: {
            setBuzzer(false);
            break;
        }

        case STATE_WARNING: {
            // Còi kêu ngắt quãng (bíp 150ms mỗi 1.5 giây).
            unsigned long cycle = (now - _lastToggleTime) % 1500;
            if (cycle < 150) {
                setBuzzer(true);
            } else {
                setBuzzer(false);
            }
            break;
        }

        case STATE_DANGER: {
            // Nguy hiểm: còi hụ dồn dập (120ms ON / 120ms OFF).
            if (now - _lastToggleTime >= 120) {
                _lastToggleTime = now;
                _toggleState = !_toggleState;
                setBuzzer(_toggleState);
            }
            break;
        }
    }
}

void AlarmController::setBuzzer(bool active) {
    // Với còi Active Buzzer: HIGH là kêu, LOW là tắt
    digitalWrite(_buzzerPin, active ? HIGH : LOW);
}
