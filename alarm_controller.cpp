/**
 * @file alarm_controller.cpp
 * @brief Hiện thực hệ thống cảnh báo đa tầng bằng Còi và LED
 */

#include "alarm_controller.h"

AlarmController::AlarmController(uint8_t buzzerPin, uint8_t greenLedPin, uint8_t yellowLedPin, uint8_t redLedPin)
    : _buzzerPin(buzzerPin),
      _greenLedPin(greenLedPin),
      _yellowLedPin(yellowLedPin),
      _redLedPin(redLedPin),
      _currentState(STATE_WARMING_UP),
      _lastToggleTime(0),
      _toggleState(false),
      _beepEndTime(0),
      _isBeepingOnce(false) {}

void AlarmController::begin() {
    pinMode(_buzzerPin, OUTPUT);
    pinMode(_greenLedPin, OUTPUT);
    pinMode(_yellowLedPin, OUTPUT);
    pinMode(_redLedPin, OUTPUT);

    setBuzzer(false);
    setLeds(false, false, false);
}

void AlarmController::setState(SystemState newState) {
    if (_currentState != newState) {
        _currentState = newState;
        _lastToggleTime = millis();
        _toggleState = false;

        // Reset trạng thái tức thì khi đổi level
        if (_currentState == STATE_SAFE) {
            setLeds(true, false, false);
            setBuzzer(false);
        } else if (_currentState == STATE_WARNING) {
            setLeds(false, true, false);
        } else if (_currentState == STATE_DANGER) {
            setLeds(false, false, true);
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
            // Chớp nháy luân phiên LED Vàng và Xanh (chu kỳ 500ms) báo đang khởi động
            if (now - _lastToggleTime >= 500) {
                _lastToggleTime = now;
                _toggleState = !_toggleState;
                setLeds(_toggleState, !_toggleState, false);
            }
            setBuzzer(false);
            break;
        }

        case STATE_SAFE: {
            setLeds(true, false, false);
            setBuzzer(false);
            break;
        }

        case STATE_WARNING: {
            // LED Vàng sáng liên tục, còi kêu ngắt quãng (Bíp 150ms mỗi 1.5 giây)
            setLeds(false, true, false);
            unsigned long cycle = (now - _lastToggleTime) % 1500;
            if (cycle < 150) {
                setBuzzer(true);
            } else {
                setBuzzer(false);
            }
            break;
        }

        case STATE_DANGER: {
            // Nguy hiểm: LED Đỏ chớp nhanh (120ms), Còi hụ dồn dập (120ms ON / 120ms OFF)
            if (now - _lastToggleTime >= 120) {
                _lastToggleTime = now;
                _toggleState = !_toggleState;
                setLeds(false, false, _toggleState);
                setBuzzer(_toggleState);
            }
            break;
        }
    }
}

void AlarmController::setLeds(bool green, bool yellow, bool red) {
    digitalWrite(_greenLedPin, green ? HIGH : LOW);
    digitalWrite(_yellowLedPin, yellow ? HIGH : LOW);
    digitalWrite(_redLedPin, red ? HIGH : LOW);
}

void AlarmController::setBuzzer(bool active) {
    // Với còi Active Buzzer: HIGH là kêu, LOW là tắt
    digitalWrite(_buzzerPin, active ? HIGH : LOW);
}
