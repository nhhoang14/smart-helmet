#pragma once
#include <stdint.h>
#include "fall_detector.h"
namespace helmet {
class AlarmPolicy {
public:
    enum Source : uint8_t { FALL = 1, GAS_WARNING = 2, GAS_DANGER = 4, SOS = 8, REMOTE = 16 };
    void update(uint8_t sources) { sources_ = sources; muted_ &= sources; }
    void acknowledge() { muted_ |= sources_; }
    FallState soundState(bool fallPending) const {
        const uint8_t audible = sources_ & ~muted_;
        if (audible & (GAS_DANGER | SOS | REMOTE)) return FallState::FALL_ALERT;
        if (audible & FALL) return fallPending ? FallState::PENDING_CONFIRMATION : FallState::FALL_ALERT;
        if (audible & GAS_WARNING) return FallState::PENDING_CONFIRMATION;
        return FallState::MONITORING;
    }
private:
    uint8_t sources_ = 0;
    uint8_t muted_ = 0;
};
}
