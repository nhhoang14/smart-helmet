/**
 * @file mq135_sensor.cpp
 * @brief Hiện thực module đọc cảm biến MQ-135 với bộ lọc chống nhiễu
 */

#include "mq135_sensor.h"

MQ135Sensor::MQ135Sensor(uint8_t analogPin, uint8_t digitalPin)
    : _analogPin(analogPin),
      _digitalPin(digitalPin),
      _startTime(0),
      _lastSampleTime(0),
      _rawADC(0),
      _filteredADC(0.0f),
      _voltage(0.0f),
      _warmedUp(false) {}

void MQ135Sensor::begin() {
    pinMode(_analogPin, INPUT);
    if (_digitalPin != 255) {
        pinMode(_digitalPin, INPUT);
    }

    // Cấu hình ADC cho ESP32 (Độ phân giải 12-bit: 0 - 4095)
    analogReadResolution(12);
    // Độ nhạy suy giảm 11dB (đo điện áp ngõ vào xấp xỉ 0 - 3.1V ~ 3.3V)
    analogSetAttenuation(ADC_11db);

    _startTime = millis();
    _lastSampleTime = millis();

    // Khởi tạo giá trị ban đầu cho bộ lọc
    _rawADC = readOversampledADC(16);
    _filteredADC = (float)_rawADC;
    _voltage = (_filteredADC / 4095.0f) * 3.3f;
}

void MQ135Sensor::update() {
    unsigned long now = millis();

    // Kiểm tra trạng thái sấy (warm-up)
    if (!_warmedUp) {
        if ((now - _startTime) >= ((unsigned long)SENSOR_WARMUP_SECONDS * 1000UL)) {
            _warmedUp = true;
        }
    }

    // Lấy mẫu theo chu kỳ không chặn (non-blocking)
    if (now - _lastSampleTime >= SENSOR_SAMPLE_INTERVAL_MS) {
        _lastSampleTime = now;

        _rawADC = readOversampledADC(8);

        // Lọc thông thấp làm mượt tín hiệu (Exponential Moving Average)
        _filteredADC = (FILTER_ALPHA * (float)_rawADC) + ((1.0f - FILTER_ALPHA) * _filteredADC);

        // Tính điện áp ước tính tại chân ESP32 (tối đa ~3.3V)
        _voltage = (_filteredADC / 4095.0f) * 3.3f;
    }
}

int MQ135Sensor::readOversampledADC(uint8_t samples) {
    if (samples == 0) samples = 1;
    uint32_t sum = 0;
    for (uint8_t i = 0; i < samples; i++) {
        sum += analogRead(_analogPin);
        delayMicroseconds(50);
    }
    return (int)(sum / samples);
}

bool MQ135Sensor::isWarmedUp() const {
    return _warmedUp;
}

uint32_t MQ135Sensor::getRemainingWarmupTime() const {
    if (_warmedUp) return 0;
    unsigned long elapsed = millis() - _startTime;
    unsigned long total = (unsigned long)SENSOR_WARMUP_SECONDS * 1000UL;
    if (elapsed >= total) return 0;
    return (uint32_t)((total - elapsed) / 1000UL);
}

int MQ135Sensor::getRawADC() const {
    return _rawADC;
}

float MQ135Sensor::getFilteredADC() const {
    return _filteredADC;
}

float MQ135Sensor::getVoltage() const {
    return _voltage;
}

bool MQ135Sensor::isDigitalTriggered() const {
    if (_digitalPin == 255) return false;
    // Module MQ thường có ngõ ra DO với LM393: Mức LOW khi vượt ngưỡng chỉnh bằng biến trở
    return (digitalRead(_digitalPin) == LOW);
}
