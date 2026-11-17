#pragma once

#include <stdint.h>

namespace helmet {

struct Vector3 {
    float x, y, z;
};

struct FallConfig {
    float impactG = 2.5f;
    float triggerDps = 200.0f;
    float tiltDegrees = 60.0f;
    float stableMinG = 0.85f;
    float stableMaxG = 1.15f;
    float stableDps = 15.0f;
    uint32_t windowMs = 5000;
    uint32_t holdMs = 3000;
    // Set a branch's hold time to zero to disable that branch.
    uint32_t slowHoldMs = 0;
    uint32_t movingWindowMs = 6000;
    uint32_t movingHoldMs = 4000;
    float orientationCorrectionSeconds = 0.5f;
    uint32_t confirmationMs = 10000;
    uint32_t maxSampleGapMs = 60;
};

enum class FallState { MONITORING, CHECKING_POSTURE, PENDING_CONFIRMATION, FALL_ALERT };
enum class FallRoute { NONE, FAST_POSTURE, SLOW_POSTURE, MOVING_POSTURE };

struct FallReport {
    FallState state;
    uint32_t timestamp;
    uint32_t heldMs;
    float accelerationG;
    float rotationDps;
    float tiltDegrees;
    const char* reason;
    FallRoute route;
    uint32_t slowHeldMs;
    uint32_t tiltedMs;
};

using FallObserver = void (*)(const FallReport&);

// Hardware-independent detector. All times come from the caller's monotonic clock.
class FallDetector {
public:
    explicit FallDetector(FallObserver observer = nullptr, FallConfig config = FallConfig{});
    bool setReference(Vector3 gravity, Vector3 gyroBias);
    bool setImpactThreshold(float impactG);
    void sample(Vector3 acceleration, Vector3 gyro, uint32_t now, bool wornBeforeTrigger = true);
    void missingSample(uint32_t now, const char* reason = "invalid or missing sample");
    void tick(uint32_t now);
    void confirm(uint32_t now);
    void clearAlert(uint32_t now);
    FallState state() const { return state_; }
    bool hasReference() const { return hasReference_; }
    float tiltDegrees() const { return tiltDegrees_; }

private:
    void emit(uint32_t now, const char* reason);
    void transition(FallState next, uint32_t now, const char* reason);
    void resetHold(uint32_t now, const char* reason);
    void resetEvidence(uint32_t now, const char* reason);
    void updateOrientation(Vector3 acceleration, Vector3 gyro, uint32_t elapsedMs);
    void updateSlowPosture(bool stableTilt, bool canStart, uint32_t now);
    void beginConfirmation(FallRoute route, uint32_t now, const char* reason);
    uint32_t observationWindowMs() const;

    FallConfig config_;
    FallObserver observer_;
    FallState state_ = FallState::MONITORING;
    Vector3 gravity_{};
    Vector3 gyroBias_{};
    Vector3 estimatedGravity_{};
    FallRoute route_ = FallRoute::NONE;
    bool hasReference_ = false;
    bool holding_ = false;
    bool hasSample_ = false;
    bool orientationValid_ = false;
    bool slowHolding_ = false;
    bool previousTilted_ = false;
    bool fastWindowClosed_ = false;
    uint32_t openedAt_ = 0;
    uint32_t pendingAt_ = 0;
    uint32_t heldAt_ = 0;
    uint32_t lastSample_ = 0;
    uint32_t slowHeldAt_ = 0;
    uint32_t tiltedMs_ = 0;
    float accelerationG_ = 0;
    float rotationDps_ = 0;
    float tiltDegrees_ = 0;
};

const char* fallStateName(FallState state);
const char* fallRouteName(FallRoute route);

} // namespace helmet
