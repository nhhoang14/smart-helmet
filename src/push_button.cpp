#include "push_button.h"

namespace helmet {

void PushButton::begin(bool pressed, uint32_t now) {
    rawPressed_ = pressed;
    stablePressed_ = pressed;
    changedAt_ = now;
    // A button held during startup must first be released before accepting a press.
    armed_ = !pressed;
}

ButtonEvent PushButton::update(bool pressed, uint32_t now) {
    if (pressed != rawPressed_) {
        rawPressed_ = pressed;
        changedAt_ = now;
    }
    if (rawPressed_ == stablePressed_ || now - changedAt_ < debounceMs_) {
        return ButtonEvent::NONE;
    }
    stablePressed_ = rawPressed_;
    if (!armed_) {
        if (!stablePressed_) armed_ = true;
        return ButtonEvent::NONE;
    }
    return stablePressed_ ? ButtonEvent::PRESSED : ButtonEvent::RELEASED;
}

void ConfirmationButton::begin(bool pressed, uint32_t now) {
    button_.begin(pressed, now);
    wasAwaiting_ = false;
    armedForPrompt_ = false;
    eligiblePress_ = false;
}

bool ConfirmationButton::update(bool pressed, bool awaitingConfirmation, uint32_t now) {
    const ButtonEvent event = button_.update(pressed, now);
    if (awaitingConfirmation != wasAwaiting_) {
        eligiblePress_ = false;
        // Require a new press after the prompt. A pre-held button cannot confirm.
        armedForPrompt_ = awaitingConfirmation && !pressed;
        wasAwaiting_ = awaitingConfirmation;
    }
    if (!awaitingConfirmation) {
        eligiblePress_ = false;
        return false;
    }
    if (event == ButtonEvent::PRESSED) {
        pressedAt_ = now;
        eligiblePress_ = armedForPrompt_;
    }
    if (event == ButtonEvent::RELEASED) {
        const bool confirmed = eligiblePress_ && now - pressedAt_ <= maxPressMs_;
        eligiblePress_ = false;
        armedForPrompt_ = true;
        return confirmed;
    }
    return false;
}

} // namespace helmet
