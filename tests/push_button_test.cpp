#include "push_button.h"
#include "fall_detector.h"

#include <stdio.h>
#include <stdlib.h>

using helmet::ButtonEvent;
using helmet::PushButton;
using helmet::ConfirmationButton;
using helmet::FallDetector;
using helmet::FallState;

namespace {
int checks = 0;

#define CHECK_BUTTON(condition) do { \
    ++checks; \
    if (!(condition)) { \
        fprintf(stderr, "FAIL button line %d: %s\n", __LINE__, #condition); \
        exit(1); \
    } \
} while (0)

void testDebounce() {
    PushButton button;
    button.begin(false, 0);
    CHECK_BUTTON(button.update(true, 100) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(false, 105) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(true, 110) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(true, 139) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(true, 140) == ButtonEvent::PRESSED);
    CHECK_BUTTON(button.update(true, 200) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(false, 220) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(true, 225) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(false, 230) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(false, 259) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(false, 260) == ButtonEvent::RELEASED);
    CHECK_BUTTON(button.update(false, 500) == ButtonEvent::NONE);
}

void testHeldAtStartup() {
    PushButton button;
    button.begin(true, 100);
    CHECK_BUTTON(button.update(true, 1000) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(false, 1020) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(false, 1050) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(true, 1100) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(true, 1130) == ButtonEvent::PRESSED);
    CHECK_BUTTON(button.update(false, 1200) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(false, 1230) == ButtonEvent::RELEASED);
}

void testRollover() {
    PushButton button;
    const uint32_t start = UINT32_MAX - 10;
    button.begin(false, start);
    CHECK_BUTTON(button.update(true, start + 5) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(true, start + 34) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(true, start + 35) == ButtonEvent::PRESSED);
    CHECK_BUTTON(button.update(false, start + 50) == ButtonEvent::NONE);
    CHECK_BUTTON(button.update(false, start + 80) == ButtonEvent::RELEASED);
}

void testShortConfirmation() {
    ConfirmationButton button;
    button.begin(false, 0);
    CHECK_BUTTON(!button.update(false, true, 100));
    CHECK_BUTTON(!button.update(true, true, 120));
    CHECK_BUTTON(!button.update(true, true, 150));
    CHECK_BUTTON(!button.update(false, true, 200));
    CHECK_BUTTON(button.update(false, true, 230));
    CHECK_BUTTON(!button.update(false, true, 300));

    ConfirmationButton exact;
    exact.begin(false, 0);
    CHECK_BUTTON(!exact.update(false, true, 100));
    CHECK_BUTTON(!exact.update(true, true, 120));
    CHECK_BUTTON(!exact.update(true, true, 150));
    CHECK_BUTTON(!exact.update(false, true, 1120));
    CHECK_BUTTON(exact.update(false, true, 1150));

    ConfirmationButton longPress;
    longPress.begin(false, 0);
    CHECK_BUTTON(!longPress.update(false, true, 100));
    CHECK_BUTTON(!longPress.update(true, true, 120));
    CHECK_BUTTON(!longPress.update(true, true, 150));
    CHECK_BUTTON(!longPress.update(false, true, 1121));
    CHECK_BUTTON(!longPress.update(false, true, 1151));
    CHECK_BUTTON(!longPress.update(true, true, 1200));
    CHECK_BUTTON(!longPress.update(true, true, 1230));
    CHECK_BUTTON(!longPress.update(false, true, 1300));
    CHECK_BUTTON(longPress.update(false, true, 1330));
}

void testPreheldAndPreviousPrompt() {
    ConfirmationButton preheld;
    preheld.begin(false, 0);
    CHECK_BUTTON(!preheld.update(true, false, 100));
    CHECK_BUTTON(!preheld.update(true, false, 130));
    CHECK_BUTTON(!preheld.update(true, true, 140));
    CHECK_BUTTON(!preheld.update(false, true, 200));
    CHECK_BUTTON(!preheld.update(false, true, 230));
    CHECK_BUTTON(!preheld.update(true, true, 300));
    CHECK_BUTTON(!preheld.update(true, true, 330));
    CHECK_BUTTON(!preheld.update(false, true, 400));
    CHECK_BUTTON(preheld.update(false, true, 430));

    // A raw press already in debounce when the prompt starts is also ignored.
    ConfirmationButton inDebounce;
    inDebounce.begin(false, 0);
    CHECK_BUTTON(!inDebounce.update(true, false, 100));
    CHECK_BUTTON(!inDebounce.update(true, true, 110));
    CHECK_BUTTON(!inDebounce.update(true, true, 130));
    CHECK_BUTTON(!inDebounce.update(false, true, 200));
    CHECK_BUTTON(!inDebounce.update(false, true, 230));

    // Holding across an expired/cancelled prompt cannot confirm a later event.
    ConfirmationButton previous;
    previous.begin(false, 0);
    CHECK_BUTTON(!previous.update(false, true, 100));
    CHECK_BUTTON(!previous.update(true, true, 120));
    CHECK_BUTTON(!previous.update(true, true, 150));
    CHECK_BUTTON(!previous.update(true, false, 200));
    CHECK_BUTTON(!previous.update(true, true, 300));
    CHECK_BUTTON(!previous.update(false, true, 400));
    CHECK_BUTTON(!previous.update(false, true, 430));

    ConfirmationButton boot;
    boot.begin(true, 0);
    CHECK_BUTTON(!boot.update(true, true, 100));
    CHECK_BUTTON(!boot.update(false, true, 200));
    CHECK_BUTTON(!boot.update(false, true, 230));
}

struct ConfirmationFixture {
    FallDetector detector;
    ConfirmationButton button;

    ConfirmationFixture() {
        button.begin(false, 0);
        CHECK_BUTTON(detector.setReference({0, 0, 1}, {0, 0, 0}));
        detector.sample({0, 0, 3}, {0, 0, 0}, 100);
        for (uint32_t now = 120; now <= 3120; now += 20) {
            detector.sample({1, 0, 0}, {0, 0, 0}, now);
            input(false, now);
        }
        CHECK_BUTTON(detector.state() == FallState::PENDING_CONFIRMATION);
    }

    void input(bool pressed, uint32_t now) {
        // This is the same ordering as the firmware loop: deadline before button.
        detector.tick(now);
        if (button.update(pressed, detector.state() == FallState::PENDING_CONFIRMATION, now)) {
            detector.confirm(now);
        }
    }
};

void testDetectorConfirmationDeadline() {
    ConfirmationFixture valid;
    valid.detector.missingSample(4000);
    valid.input(true, 4100);
    valid.input(true, 4130);
    valid.input(false, 4200);
    valid.input(false, 4230);
    CHECK_BUTTON(valid.detector.state() == FallState::MONITORING);
    valid.detector.tick(20000);
    CHECK_BUTTON(valid.detector.state() == FallState::MONITORING);

    ConfirmationFixture beforeDeadline;
    beforeDeadline.input(true, 13050);
    beforeDeadline.input(true, 13080);
    beforeDeadline.input(false, 13089);
    beforeDeadline.input(false, 13119);
    CHECK_BUTTON(beforeDeadline.detector.state() == FallState::MONITORING);

    ConfirmationFixture atDeadline;
    atDeadline.input(true, 13050);
    atDeadline.input(true, 13080);
    atDeadline.input(false, 13090);
    atDeadline.input(false, 13120);
    CHECK_BUTTON(atDeadline.detector.state() == FallState::FALL_ALERT);

    ConfirmationFixture late;
    late.input(true, 13080);
    late.input(true, 13110);
    late.input(false, 13120);
    late.input(false, 13150);
    CHECK_BUTTON(late.detector.state() == FallState::FALL_ALERT);
}

void testConfirmationRollover() {
    const uint32_t start = UINT32_MAX - 100;
    ConfirmationButton button;
    button.begin(false, start);
    CHECK_BUTTON(!button.update(false, true, start + 10));
    CHECK_BUTTON(!button.update(true, true, start + 20));
    CHECK_BUTTON(!button.update(true, true, start + 50));
    CHECK_BUTTON(!button.update(false, true, start + 100));
    CHECK_BUTTON(button.update(false, true, start + 130));
}
} // namespace

int runPushButtonTests() {
    testDebounce();
    testHeldAtStartup();
    testRollover();
    testShortConfirmation();
    testPreheldAndPreviousPrompt();
    testDetectorConfirmationDeadline();
    testConfirmationRollover();
    return checks;
}
