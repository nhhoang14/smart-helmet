#include "fall_alarm.h"

namespace helmet {

void FallAlarm::write(bool active) {
    if (outputKnown_ && active_ == active) return;
    active_ = active;
    outputKnown_ = true;
    if (output_) output_(active);
}

void FallAlarm::begin() {
    state_ = FallState::MONITORING;
    testing_ = false;
    outputKnown_ = false;
    write(false);
}

void FallAlarm::update(FallState state, uint32_t now) {
    if (state_ != state) {
        state_ = state;
        stateStartedAt_ = now;
        testing_ = false;
    }

    const uint32_t elapsed = now - stateStartedAt_;
    switch (state_) {
        case FallState::PENDING_CONFIRMATION:
            write(elapsed % 1500 < 150);
            return;
        case FallState::FALL_ALERT:
            write(elapsed % 240 < 120);
            return;
        case FallState::MONITORING:
            if (testing_ && now - testStartedAt_ >= 200) testing_ = false;
            write(testing_);
            return;
        case FallState::CHECKING_POSTURE:
            write(false);
            return;
    }
}

bool FallAlarm::testBeep(uint32_t now) {
    if (state_ != FallState::MONITORING) return false;
    testStartedAt_ = now;
    testing_ = true;
    write(true);
    return true;
}

} // namespace helmet
