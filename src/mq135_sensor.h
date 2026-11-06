/**
 * @file mq135_sensor.h
 * @brief Module đọc và xử lý tín hiệu cảm biến MQ-135
 */

#ifndef MQ135_SENSOR_H
#define MQ135_SENSOR_H

#include <Arduino.h>
#include "config.h"

class MQ135Sensor {
public:
    MQ135Sensor(uint8_t analogPin, uint8_t digitalPin = 255);

    void begin();
    void update();

    bool isWarmedUp() const;
    uint32_t getRemainingWarmupTime() const;

    int getRawADC() const;
    float getFilteredADC() const;
    float getVoltage() const;

    // Trả về true nếu chân DO (digital) kích hoạt (active LOW thông thường trên module MQ)
    bool isDigitalTriggered() const;

private:
    uint8_t _analogPin;
    uint8_t _digitalPin;
    unsigned long _startTime;
    unsigned long _lastSampleTime;
    
    int _rawADC;
    float _filteredADC;
    float _voltage;
    bool _warmedUp;

    int readOversampledADC(uint8_t samples = 16);
};

#endif // MQ135_SENSOR_H
