#pragma once

#include <stdint.h>

namespace helmet {

enum class ButtonEvent { NONE, PRESSED, RELEASED };

// Input is a logical pressed level. The hardware adapter handles active-low wiring.
class PushButton {
public:
    explicit PushButton(uint32_t debounceMs = 30) : debounceMs_(debounceMs) {}
    void begin(bool pressed, uint32_t now);
    ButtonEvent update(bool pressed, uint32_t now);

private:
    uint32_t debounceMs_;
    uint32_t changedAt_ = 0;
    bool rawPressed_ = false;
    bool stablePressed_ = false;
    bool armed_ = true;
};

class ConfirmationButton {
public:
    explicit ConfirmationButton(uint32_t maxPressMs = 1000) : maxPressMs_(maxPressMs) {}
    void begin(bool pressed, uint32_t now);
    bool update(bool pressed, bool awaitingConfirmation, uint32_t now);

private:
    PushButton button_;
    uint32_t maxPressMs_;
    uint32_t pressedAt_ = 0;
    bool wasAwaiting_ = false;
    bool armedForPrompt_ = false;
    bool eligiblePress_ = false;
};

} // namespace helmet
