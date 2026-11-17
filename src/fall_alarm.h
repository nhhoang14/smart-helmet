#pragma once

#include "fall_detector.h"

namespace helmet {

using BuzzerOutput = void (*)(bool active);

// Schedules the sound envelope; the output adapter generates PWM or DC as needed.
class FallAlarm {
public:
    explicit FallAlarm(BuzzerOutput output) : output_(output) {}
    void begin();
    void update(FallState state, uint32_t now);
    bool testBeep(uint32_t now);

private:
    void write(bool active);

    BuzzerOutput output_;
    FallState state_ = FallState::MONITORING;
    uint32_t stateStartedAt_ = 0;
    uint32_t testStartedAt_ = 0;
    bool testing_ = false;
    bool active_ = false;
    bool outputKnown_ = false;
};

} // namespace helmet
