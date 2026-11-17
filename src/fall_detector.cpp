#include "fall_detector.h"

#include <math.h>

namespace helmet {
namespace {
constexpr float RADIANS_PER_DEGREE = 0.0174532925f;

float length(Vector3 v) {
    return sqrtf(v.x * v.x + v.y * v.y + v.z * v.z);
}

bool finiteVector(Vector3 v) {
    return isfinite(v.x) && isfinite(v.y) && isfinite(v.z);
}

Vector3 normalized(Vector3 v) {
    const float magnitude = length(v);
    return {v.x / magnitude, v.y / magnitude, v.z / magnitude};
}

float angle(Vector3 a, Vector3 b) {
    const float denominator = length(a) * length(b);
    if (!isfinite(denominator) || denominator < 0.000001f) return NAN;
    const float cosine = (a.x * b.x + a.y * b.y + a.z * b.z) / denominator;
    if (!isfinite(cosine)) return NAN;
    return acosf(fmaxf(-1.0f, fminf(1.0f, cosine))) / RADIANS_PER_DEGREE;
}

Vector3 predictGravity(Vector3 gravity, Vector3 gyro, float seconds) {
    const float speed = length(gyro);
    if (speed < 0.000001f) return gravity;
    const Vector3 axis = normalized(gyro);
    // Gravity in sensor coordinates rotates opposite to the sensor's rotation.
    const float rotation = -speed * RADIANS_PER_DEGREE * seconds;
    const float sine = sinf(rotation);
    const float cosine = cosf(rotation);
    const float dot = axis.x * gravity.x + axis.y * gravity.y + axis.z * gravity.z;
    const Vector3 cross{axis.y * gravity.z - axis.z * gravity.y,
                        axis.z * gravity.x - axis.x * gravity.z,
                        axis.x * gravity.y - axis.y * gravity.x};
    return {gravity.x * cosine + cross.x * sine + axis.x * dot * (1 - cosine),
            gravity.y * cosine + cross.y * sine + axis.y * dot * (1 - cosine),
            gravity.z * cosine + cross.z * sine + axis.z * dot * (1 - cosine)};
}
} // namespace

const char* fallStateName(FallState state) {
    switch (state) {
        case FallState::MONITORING: return "MONITORING";
        case FallState::CHECKING_POSTURE: return "CHECKING_POSTURE";
        case FallState::PENDING_CONFIRMATION: return "PENDING_CONFIRMATION";
        case FallState::FALL_ALERT: return "FALL_ALERT";
    }
    return "UNKNOWN";
}

const char* fallRouteName(FallRoute route) {
    switch (route) {
        case FallRoute::NONE: return "NONE";
        case FallRoute::FAST_POSTURE: return "FAST_POSTURE";
        case FallRoute::SLOW_POSTURE: return "SLOW_POSTURE";
        case FallRoute::MOVING_POSTURE: return "MOVING_POSTURE";
    }
    return "UNKNOWN";
}

FallDetector::FallDetector(FallObserver observer, FallConfig config)
    : config_(config), observer_(observer) {
    tiltDegrees_ = NAN;
}

bool FallDetector::setImpactThreshold(float impactG) {
    if (state_ != FallState::MONITORING || !isfinite(impactG) || impactG <= 1.15f || impactG > 16.0f) return false;
    config_.impactG = impactG;
    return true;
}

bool FallDetector::setReference(Vector3 gravity, Vector3 gyroBias) {
    const float gravityLength = length(gravity);
    if (state_ != FallState::MONITORING || !finiteVector(gravity) ||
        !finiteVector(gyroBias) || !isfinite(gravityLength) ||
        gravityLength < 0.8f || gravityLength > 1.2f || length(gyroBias) >= 10.0f) {
        return false;
    }
    gravity_ = gravity;
    gyroBias_ = gyroBias;
    hasReference_ = true;
    holding_ = false;
    slowHolding_ = false;
    tiltedMs_ = 0;
    previousTilted_ = false;
    hasSample_ = false;
    orientationValid_ = false;
    tiltDegrees_ = NAN;
    return true;
}

void FallDetector::emit(uint32_t now, const char* reason) {
    if (observer_) {
        observer_({state_, now, holding_ ? uint32_t(now - heldAt_) : 0,
                   accelerationG_, rotationDps_, tiltDegrees_, reason, route_,
                   slowHolding_ ? uint32_t(now - slowHeldAt_) : 0, tiltedMs_});
    }
}

void FallDetector::transition(FallState next, uint32_t now, const char* reason) {
    // A slow posture can continue beyond the impact observation window.
    const bool preserveSlow = state_ == FallState::CHECKING_POSTURE && next == FallState::MONITORING;
    state_ = next;
    emit(now, reason);
    holding_ = false;
    if (!preserveSlow) slowHolding_ = false;
    tiltedMs_ = 0;
    previousTilted_ = false;
    if (next == FallState::MONITORING) route_ = FallRoute::NONE;
}

void FallDetector::resetHold(uint32_t now, const char* reason) {
    if (holding_) emit(now, reason);
    holding_ = false;
}

void FallDetector::resetEvidence(uint32_t now, const char* reason) {
    if (holding_ || slowHolding_ || tiltedMs_ != 0) emit(now, reason);
    holding_ = false;
    slowHolding_ = false;
    tiltedMs_ = 0;
    previousTilted_ = false;
}

uint32_t FallDetector::observationWindowMs() const {
    if (config_.movingHoldMs != 0 && config_.movingWindowMs > config_.windowMs) {
        return config_.movingWindowMs;
    }
    return config_.windowMs;
}

void FallDetector::tick(uint32_t now) {
    if (hasSample_ && now - lastSample_ > config_.maxSampleGapMs) {
        resetEvidence(now, "sample gap; all posture timers reset");
        hasSample_ = false;
        orientationValid_ = false;
        tiltDegrees_ = NAN;
    }
    if (state_ == FallState::CHECKING_POSTURE) {
        const uint32_t elapsed = now - openedAt_;
        if (!fastWindowClosed_ && elapsed > config_.windowMs) {
            resetHold(now, "fast posture window expired");
            fastWindowClosed_ = true;
        }
        // Exactly at the deadline, sample() may still complete a valid hold.
        if (elapsed > observationWindowMs()) {
            transition(FallState::MONITORING, now, "posture window expired");
        }
    }
    if (state_ == FallState::PENDING_CONFIRMATION &&
        now - pendingAt_ >= config_.confirmationMs) {
        transition(FallState::FALL_ALERT, now, "confirmation timeout");
    }
}

void FallDetector::missingSample(uint32_t now, const char* reason) {
    accelerationG_ = rotationDps_ = tiltDegrees_ = NAN;
    resetEvidence(now, reason);
    hasSample_ = false;
    orientationValid_ = false;
    tick(now);
}

void FallDetector::updateOrientation(Vector3 acceleration, Vector3 gyro, uint32_t elapsedMs) {
    const bool nearGravity = accelerationG_ >= config_.stableMinG &&
                             accelerationG_ <= config_.stableMaxG;
    if (nearGravity && rotationDps_ < config_.stableDps) {
        // In a quasi-static sample, acceleration gives the absolute gravity direction.
        estimatedGravity_ = normalized(acceleration);
        orientationValid_ = true;
    } else if (orientationValid_) {
        const float seconds = elapsedMs * 0.001f;
        estimatedGravity_ = predictGravity(estimatedGravity_, gyro, seconds);
        if (nearGravity && seconds > 0) {
            const float weight = seconds / (config_.orientationCorrectionSeconds + seconds);
            const Vector3 measured = normalized(acceleration);
            estimatedGravity_ = {estimatedGravity_.x * (1 - weight) + measured.x * weight,
                                 estimatedGravity_.y * (1 - weight) + measured.y * weight,
                                 estimatedGravity_.z * (1 - weight) + measured.z * weight};
        }
        const float magnitude = length(estimatedGravity_);
        orientationValid_ = finiteVector(estimatedGravity_) && isfinite(magnitude) &&
                            magnitude > 0.000001f;
        if (orientationValid_) estimatedGravity_ = normalized(estimatedGravity_);
    }
    tiltDegrees_ = NAN;
    if (orientationValid_) tiltDegrees_ = angle(gravity_, estimatedGravity_);
}

void FallDetector::beginConfirmation(FallRoute route, uint32_t now, const char* reason) {
    route_ = route;
    pendingAt_ = now;
    transition(FallState::PENDING_CONFIRMATION, now, reason);
}

void FallDetector::updateSlowPosture(bool stableTilt, bool canStart, uint32_t now) {
    if (config_.slowHoldMs == 0) return;
    if (!stableTilt) {
        if (slowHolding_) emit(now, "slow posture interrupted");
        slowHolding_ = false;
        return;
    }
    if (!slowHolding_) {
        if (!canStart) return;
        slowHolding_ = true;
        slowHeldAt_ = now;
        emit(now, "slow tilted posture started");
    } else if (now - slowHeldAt_ >= config_.slowHoldMs) {
        beginConfirmation(FallRoute::SLOW_POSTURE, now, "prolonged stable tilt without impact required");
    }
}

void FallDetector::sample(Vector3 acceleration, Vector3 gyro, uint32_t now,
                          bool wornBeforeTrigger) {
    tick(now);
    if (!hasReference_) return;
    if (!finiteVector(acceleration) || !finiteVector(gyro)) {
        missingSample(now);
        return;
    }
    const Vector3 correctedGyro{gyro.x - gyroBias_.x, gyro.y - gyroBias_.y,
                               gyro.z - gyroBias_.z};
    accelerationG_ = length(acceleration);
    rotationDps_ = length(correctedGyro);
    if (!isfinite(accelerationG_) || accelerationG_ < 0.000001f || !isfinite(rotationDps_)) {
        missingSample(now);
        return;
    }
    const uint32_t elapsedMs = hasSample_ ? uint32_t(now - lastSample_) : 0;
    updateOrientation(acceleration, correctedGyro, elapsedMs);
    lastSample_ = now;
    hasSample_ = true;

    const bool tilted = isfinite(tiltDegrees_) && tiltDegrees_ > config_.tiltDegrees;
    const bool stableTilt = tilted && accelerationG_ >= config_.stableMinG &&
                            accelerationG_ <= config_.stableMaxG && rotationDps_ < config_.stableDps;

    if (state_ == FallState::MONITORING) {
        if (wornBeforeTrigger && (accelerationG_ > config_.impactG ||
                                 rotationDps_ > config_.triggerDps)) {
            openedAt_ = now;
            fastWindowClosed_ = false;
            transition(FallState::CHECKING_POSTURE, now, "impact or rapid rotation");
            return;
        }
        updateSlowPosture(stableTilt, wornBeforeTrigger, now);
        return;
    }
    if (state_ != FallState::CHECKING_POSTURE) return;

    const uint32_t elapsed = now - openedAt_;
    if (config_.movingHoldMs != 0 && elapsed <= config_.movingWindowMs) {
        // Count only intervals bounded by two valid, tilted samples.
        if (tilted && previousTilted_) tiltedMs_ += elapsedMs;
        previousTilted_ = tilted;
    }

    if (!fastWindowClosed_ && elapsed <= config_.windowMs) {
        if (!stableTilt) {
            resetHold(now, "posture condition interrupted");
        } else if (!holding_) {
            holding_ = true;
            heldAt_ = now;
            emit(now, "stable tilted posture started");
        } else if (now - heldAt_ >= config_.holdMs) {
            beginConfirmation(FallRoute::FAST_POSTURE, now, "stable posture held long enough");
            return;
        }
    }
    if (config_.movingHoldMs != 0 && elapsed <= config_.movingWindowMs &&
        tilted && tiltedMs_ >= config_.movingHoldMs) {
        beginConfirmation(FallRoute::MOVING_POSTURE, now, "accumulated tilted posture after impact");
        return;
    }
    updateSlowPosture(stableTilt, true, now);
    if (state_ == FallState::CHECKING_POSTURE && elapsed >= observationWindowMs()) {
        transition(FallState::MONITORING, now, "posture window expired");
    }
}

void FallDetector::confirm(uint32_t now) {
    tick(now);
    if (state_ == FallState::PENDING_CONFIRMATION) {
        transition(FallState::MONITORING, now, "user confirmed OK");
    }
}

void FallDetector::clearAlert(uint32_t now) {
    if (state_ == FallState::FALL_ALERT) {
        transition(FallState::MONITORING, now, "alert acknowledged by user");
    }
}
} // namespace helmet
