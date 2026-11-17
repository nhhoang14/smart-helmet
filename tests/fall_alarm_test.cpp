#include "fall_alarm.h"
#include "push_button.h"
#include "alarm_policy.h"
#include "gas_policy.h"

#include <stdio.h>
#include <stdlib.h>

using helmet::FallAlarm;
using helmet::FallDetector;
using helmet::FallReport;
using helmet::FallState;

namespace {
int checks = 0;
bool buzzerActive = false;
int outputChanges = 0;

#define CHECK_ALARM(condition) do { \
    ++checks; \
    if (!(condition)) { \
        fprintf(stderr, "FAIL alarm line %d: %s\n", __LINE__, #condition); \
        exit(1); \
    } \
} while (0)

void writeBuzzer(bool active) {
    buzzerActive = active;
    ++outputChanges;
}

FallAlarm alarm(writeBuzzer);

void observeDetector(const FallReport& report) {
    alarm.update(report.state, report.timestamp);
}

void testPatterns() {
    alarm.begin();
    CHECK_ALARM(!buzzerActive);
    outputChanges = 0;
    alarm.update(FallState::CHECKING_POSTURE, 20);
    CHECK_ALARM(!buzzerActive);
    CHECK_ALARM(outputChanges == 0);

    alarm.update(FallState::PENDING_CONFIRMATION, 100);
    CHECK_ALARM(buzzerActive);
    alarm.update(FallState::PENDING_CONFIRMATION, 249);
    CHECK_ALARM(buzzerActive);
    alarm.update(FallState::PENDING_CONFIRMATION, 250);
    CHECK_ALARM(!buzzerActive);
    alarm.update(FallState::PENDING_CONFIRMATION, 1599);
    CHECK_ALARM(!buzzerActive);
    alarm.update(FallState::PENDING_CONFIRMATION, 1600);
    CHECK_ALARM(buzzerActive);

    alarm.update(FallState::FALL_ALERT, 1700);
    CHECK_ALARM(buzzerActive);
    alarm.update(FallState::FALL_ALERT, 1819);
    CHECK_ALARM(buzzerActive);
    alarm.update(FallState::FALL_ALERT, 1820);
    CHECK_ALARM(!buzzerActive);
    alarm.update(FallState::FALL_ALERT, 1940);
    CHECK_ALARM(buzzerActive);
    alarm.update(FallState::MONITORING, 1941);
    CHECK_ALARM(!buzzerActive);
}

void testManualBeepAndPriority() {
    alarm.begin();
    CHECK_ALARM(alarm.testBeep(100));
    CHECK_ALARM(buzzerActive);
    alarm.update(FallState::MONITORING, 299);
    CHECK_ALARM(buzzerActive);
    alarm.update(FallState::MONITORING, 300);
    CHECK_ALARM(!buzzerActive);

    CHECK_ALARM(alarm.testBeep(400));
    alarm.update(FallState::CHECKING_POSTURE, 420);
    CHECK_ALARM(!buzzerActive);
    CHECK_ALARM(!alarm.testBeep(440));

    alarm.update(FallState::PENDING_CONFIRMATION, 500);
    CHECK_ALARM(!alarm.testBeep(520));
    alarm.update(FallState::PENDING_CONFIRMATION, 650);
    CHECK_ALARM(!buzzerActive);
    alarm.update(FallState::FALL_ALERT, 700);
    CHECK_ALARM(!alarm.testBeep(720));
    alarm.update(FallState::FALL_ALERT, 820);
    CHECK_ALARM(!buzzerActive);

    // Restarting the controller always starts with the buzzer off.
    alarm.begin();
    CHECK_ALARM(!buzzerActive);
}

void testClockRollover() {
    const uint32_t start = UINT32_MAX - 100;
    alarm.begin();
    alarm.update(FallState::PENDING_CONFIRMATION, start);
    CHECK_ALARM(buzzerActive);
    alarm.update(FallState::PENDING_CONFIRMATION, start + 150);
    CHECK_ALARM(!buzzerActive);
    alarm.update(FallState::PENDING_CONFIRMATION, start + 1500);
    CHECK_ALARM(buzzerActive);
    alarm.update(FallState::FALL_ALERT, start + 1600);
    CHECK_ALARM(buzzerActive);
    alarm.update(FallState::FALL_ALERT, start + 1720);
    CHECK_ALARM(!buzzerActive);
    alarm.begin();
    CHECK_ALARM(alarm.testBeep(start));
    alarm.update(FallState::MONITORING, start + 199);
    CHECK_ALARM(buzzerActive);
    alarm.update(FallState::MONITORING, start + 200);
    CHECK_ALARM(!buzzerActive);
}

void testDetectorIntegration() {
    alarm.begin();
    FallDetector detector(observeDetector);
    CHECK_ALARM(detector.setReference({0, 0, 1}, {0, 0, 0}));
    detector.sample({0, 0, 3}, {0, 0, 0}, 100);
    CHECK_ALARM(!buzzerActive);
    for (uint32_t now = 120; now <= 3120; now += 20) {
        detector.sample({1, 0, 0}, {0, 0, 0}, now);
        alarm.update(detector.state(), now);
    }
    CHECK_ALARM(detector.state() == FallState::PENDING_CONFIRMATION);
    CHECK_ALARM(buzzerActive);
    detector.confirm(3200);
    CHECK_ALARM(detector.state() == FallState::MONITORING);
    CHECK_ALARM(!buzzerActive);

    detector.sample({0, 0, 3}, {0, 0, 0}, 3300);
    for (uint32_t now = 3320; now <= 6320; now += 20) {
        detector.sample({1, 0, 0}, {0, 0, 0}, now);
        alarm.update(detector.state(), now);
    }
    CHECK_ALARM(detector.state() == FallState::PENDING_CONFIRMATION);
    detector.missingSample(6400);
    alarm.update(detector.state(), 6500);
    CHECK_ALARM(!buzzerActive);
    detector.tick(16320);
    CHECK_ALARM(detector.state() == FallState::FALL_ALERT);
    CHECK_ALARM(buzzerActive);
    // Sensor failure does not prevent the local emergency pattern.
    detector.missingSample(16440);
    alarm.update(detector.state(), 16440);
    CHECK_ALARM(!buzzerActive);
    detector.missingSample(16560);
    alarm.update(detector.state(), 16560);
    CHECK_ALARM(buzzerActive);
    detector.clearAlert(16561);
    CHECK_ALARM(!buzzerActive);
}
void testButtonStopsBuzzer() {
    alarm.begin();
    FallDetector detector(observeDetector);
    helmet::PushButton button;
    button.begin(false, 0);
    CHECK_ALARM(detector.setReference({0, 0, 1}, {0, 0, 0}));
    auto trigger = [&](uint32_t start) {
        detector.sample({0, 0, 3}, {0, 0, 0}, start);
        for (uint32_t elapsed = 20; elapsed <= 3020; elapsed += 20) {
            detector.sample({1, 0, 0}, {0, 0, 0}, start + elapsed);
        }
        CHECK_ALARM(detector.state() == FallState::PENDING_CONFIRMATION);
        CHECK_ALARM(buzzerActive);
    };
    auto input = [&](bool pressed, uint32_t now) {
        detector.tick(now);
        if (button.update(pressed, now) == helmet::ButtonEvent::PRESSED) {
            if (detector.state() == FallState::PENDING_CONFIRMATION) detector.confirm(now);
            else if (detector.state() == FallState::FALL_ALERT) detector.clearAlert(now);
        }
        alarm.update(detector.state(), now);
    };
    trigger(100);
    input(true, 3140);
    input(false, 3150); // Bounce cannot acknowledge.
    CHECK_ALARM(detector.state() == FallState::PENDING_CONFIRMATION);
    input(true, 3160);
    input(true, 3190);
    CHECK_ALARM(detector.state() == FallState::MONITORING);
    CHECK_ALARM(!buzzerActive); // No release required.

    trigger(3300);
    input(true, 16320); // Holding the previous press cannot cancel a new alert.
    CHECK_ALARM(detector.state() == FallState::FALL_ALERT);
    CHECK_ALARM(buzzerActive);
    input(false, 16330);
    input(false, 16360);
    input(true, 16370);
    input(true, 16400);
    CHECK_ALARM(detector.state() == FallState::MONITORING);
    CHECK_ALARM(!buzzerActive);
    input(true, 20000);
    CHECK_ALARM(!buzzerActive);
}
} // namespace

int runFallAlarmTests() {
    testPatterns();
    testManualBeepAndPriority();
    testClockRollover();
    testDetectorIntegration();
    testButtonStopsBuzzer();
    helmet::AlarmPolicy policy;
    using Policy = helmet::AlarmPolicy;
    policy.update(Policy::FALL | Policy::GAS_WARNING);
    CHECK_ALARM(policy.soundState(true) == FallState::PENDING_CONFIRMATION);
    policy.acknowledge();
    CHECK_ALARM(policy.soundState(true) == FallState::MONITORING);
    policy.update(Policy::FALL | Policy::GAS_WARNING);
    CHECK_ALARM(policy.soundState(false) == FallState::MONITORING);
    // A worsening gas condition is a new source and must still sound.
    policy.update(Policy::GAS_DANGER);
    CHECK_ALARM(policy.soundState(false) == FallState::FALL_ALERT);
    policy.acknowledge();
    CHECK_ALARM(policy.soundState(false) == FallState::MONITORING);
    policy.update(0);
    policy.update(Policy::FALL);
    CHECK_ALARM(policy.soundState(true) == FallState::PENDING_CONFIRMATION);
    CHECK_ALARM(policy.soundState(false) == FallState::FALL_ALERT);
    policy.update(Policy::FALL | Policy::SOS);
    policy.acknowledge();
    CHECK_ALARM(policy.soundState(false) == FallState::MONITORING);
    policy.update(Policy::REMOTE);
    CHECK_ALARM(policy.soundState(false) == FallState::FALL_ALERT);
    helmet::GasPolicy gas;
    using Gas = helmet::GasLevel;
    CHECK_ALARM(gas.update(700, 2500, false, true) == Gas::WARMING_UP);
    CHECK_ALARM(gas.update(700, 2500, true, false) == Gas::SAFE);
    CHECK_ALARM(gas.update(100, 500, true, true) == Gas::SAFE);
    CHECK_ALARM(gas.update(300, 500, true, true) == Gas::WARNING);
    CHECK_ALARM(gas.update(290, 500, true, true) == Gas::WARNING);
    CHECK_ALARM(gas.update(275, 500, true, true) == Gas::SAFE);
    CHECK_ALARM(gas.update(600, 500, true, true) == Gas::DANGER);
    CHECK_ALARM(gas.update(580, 500, true, true) == Gas::DANGER);
    CHECK_ALARM(gas.update(575, 500, true, true) == Gas::WARNING);
    CHECK_ALARM(gas.update(100, 2200, true, true) == Gas::DANGER);
    CHECK_ALARM(gas.update(100, 2170, true, true) == Gas::DANGER);
    CHECK_ALARM(gas.update(100, 2150, true, true) == Gas::WARNING);
    CHECK_ALARM(gas.update(100, 1150, true, true) == Gas::SAFE);
    CHECK_ALARM(gas.update(100, 500, true, true, true) == Gas::DANGER);
    CHECK_ALARM(gas.update(NAN, 500, true, true) == Gas::WARMING_UP);
    CHECK_ALARM(!gas.setThresholds(600, 300));
    CHECK_ALARM(!gas.setThresholds(NAN, 600));
    CHECK_ALARM(gas.setThresholds(400, 800));
    CHECK_ALARM(gas.update(350, 500, true, true) == Gas::SAFE);
    CHECK_ALARM(gas.update(800, 500, true, true) == Gas::DANGER);
    // Acknowledge mutes gas while it persists; falling remains an independent source.
    Policy combined;
    combined.update(Policy::GAS_DANGER);
    combined.acknowledge();
    combined.update(Policy::GAS_DANGER);
    CHECK_ALARM(combined.soundState(false) == FallState::MONITORING);
    combined.update(Policy::GAS_DANGER | Policy::FALL);
    CHECK_ALARM(combined.soundState(true) == FallState::PENDING_CONFIRMATION);
    combined.update(0);
    combined.update(Policy::GAS_DANGER);
    CHECK_ALARM(combined.soundState(false) == FallState::FALL_ALERT);
    return checks;
}
