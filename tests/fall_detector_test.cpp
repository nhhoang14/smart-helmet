#include "fall_detector.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <vector>

using helmet::FallDetector;
using helmet::FallReport;
using helmet::FallState;
using helmet::Vector3;
using helmet::FallConfig;
using helmet::FallRoute;

static std::vector<FallReport> reports;
static int checks = 0;

static void record(const FallReport& report) { reports.push_back(report); }

#define CHECK(condition) do { \
    ++checks; \
    if (!(condition)) { \
        fprintf(stderr, "FAIL line %d: %s\n", __LINE__, #condition); \
        exit(1); \
    } \
} while (0)

const Vector3 upright{0, 0, 1};
const Vector3 still{0, 0, 0};
const Vector3 sideways{1, 0, 0};

// Run original V1 contract tests with the two additional routes disabled.
static FallConfig legacyConfig() {
    FallConfig config;
    config.slowHoldMs = 0;
    config.movingHoldMs = 0;
    return config;
}

struct Fixture {
    FallDetector detector;

    explicit Fixture(FallConfig config = legacyConfig()) : detector(record, config) {
        reports.clear();
        CHECK(detector.setReference(upright, still));
    }

    void impact(uint32_t now = 100) {
        detector.sample({0, 0, 3}, still, now);
        CHECK(detector.state() == FallState::CHECKING_POSTURE);
    }

    void hold(uint32_t first, uint32_t last, Vector3 pose = sideways,
              Vector3 gyro = still, bool worn = true) {
        for (uint32_t elapsed = 0; elapsed <= uint32_t(last - first); elapsed += 20) {
            detector.sample(pose, gyro, first + elapsed, worn);
        }
    }

    void pending() {
        impact();
        hold(120, 3120);
        CHECK(detector.state() == FallState::PENDING_CONFIRMATION);
    }
};

static int alertCount() {
    int count = 0;
    for (const auto& report : reports) {
        if (report.state == FallState::FALL_ALERT) ++count;
    }
    return count;
}

static void testTriggersAndDirections() {
    const Vector3 directions[] = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
    for (const auto& direction : directions) {
        Fixture f;
        f.impact();
        f.hold(120, 3120, direction);
        CHECK(f.detector.state() == FallState::PENDING_CONFIRMATION);
    }
    Fixture gyro;
    gyro.detector.sample({0, 0, 1.8f}, {0, 230, 0}, 100);
    CHECK(gyro.detector.state() == FallState::CHECKING_POSTURE);
    gyro.hold(120, 3120);
    CHECK(gyro.detector.state() == FallState::PENDING_CONFIRMATION);

    Fixture thresholds;
    thresholds.detector.sample({0, 0, 2.5f}, {200, 0, 0}, 100);
    CHECK(thresholds.detector.state() == FallState::MONITORING);
    CHECK(!thresholds.detector.setImpactThreshold(NAN));
    CHECK(!thresholds.detector.setImpactThreshold(1.0f));
    CHECK(!thresholds.detector.setImpactThreshold(17.0f));
    CHECK(thresholds.detector.setImpactThreshold(4.0f));
    thresholds.detector.sample({0, 0, 3}, still, 120);
    CHECK(thresholds.detector.state() == FallState::MONITORING);
    thresholds.detector.sample({0, 0, 4.1f}, still, 140);
    CHECK(thresholds.detector.state() == FallState::CHECKING_POSTURE);
    CHECK(!thresholds.detector.setImpactThreshold(2.0f));
}

static void testTimeBoundaries() {
    Fixture shortHold;
    shortHold.impact();
    shortHold.hold(120, 2620);
    CHECK(shortHold.detector.state() == FallState::CHECKING_POSTURE);

    Fixture exact;
    exact.impact();
    exact.hold(2100, 5080);
    exact.detector.tick(5100);
    exact.detector.sample(sideways, still, 5100);
    CHECK(exact.detector.state() == FallState::PENDING_CONFIRMATION);

    Fixture late;
    late.impact();
    late.hold(2120, 5120);
    CHECK(late.detector.state() == FallState::MONITORING);

    Fixture repeatedImpact;
    repeatedImpact.impact();
    repeatedImpact.detector.sample({0, 0, 4}, still, 3000);
    repeatedImpact.detector.tick(5101);
    CHECK(repeatedImpact.detector.state() == FallState::MONITORING);

    Fixture absent;
    absent.impact();
    absent.detector.tick(5101);
    CHECK(absent.detector.state() == FallState::MONITORING);
}

static void testInterruptedPosture() {
    Fixture f;
    f.impact();
    f.hold(120, 2120);
    f.detector.sample(upright, still, 2140);
    f.hold(2160, 5100);
    CHECK(f.detector.state() == FallState::MONITORING);

    Fixture missing;
    missing.impact();
    missing.hold(120, 2120);
    missing.detector.missingSample(2140);
    missing.hold(2160, 5100);
    CHECK(missing.detector.state() == FallState::MONITORING);

    Fixture gap;
    gap.impact();
    gap.hold(120, 2120);
    gap.hold(2200, 5100);
    CHECK(gap.detector.state() == FallState::MONITORING);

    Fixture gapLimit;
    gapLimit.impact();
    gapLimit.hold(120, 2120);
    gapLimit.hold(2180, 3120);
    CHECK(gapLimit.detector.state() == FallState::PENDING_CONFIRMATION);

    Fixture moving;
    moving.impact();
    moving.hold(120, 5100, sideways, {15, 0, 0});
    CHECK(moving.detector.state() == FallState::MONITORING);

    Fixture acceleration;
    acceleration.impact();
    acceleration.hold(120, 5100, {1.2f, 0, 0});
    CHECK(acceleration.detector.state() == FallState::MONITORING);

    Fixture lowerBoundary;
    lowerBoundary.impact();
    lowerBoundary.hold(120, 3120, {0.85f, 0, 0});
    CHECK(lowerBoundary.detector.state() == FallState::PENDING_CONFIRMATION);

    Fixture upperBoundary;
    upperBoundary.impact();
    upperBoundary.hold(120, 3120, {1.15f, 0, 0});
    CHECK(upperBoundary.detector.state() == FallState::PENDING_CONFIRMATION);
}

static void testConfirmationAndAlert() {
    Fixture ok;
    ok.pending();
    ok.detector.confirm(13119);
    CHECK(ok.detector.state() == FallState::MONITORING);
    ok.detector.tick(20000);
    CHECK(alertCount() == 0);

    Fixture expired;
    expired.pending();
    expired.detector.tick(13119);
    CHECK(expired.detector.state() == FallState::PENDING_CONFIRMATION);
    expired.detector.confirm(13120);
    CHECK(expired.detector.state() == FallState::FALL_ALERT);
    for (uint32_t now = 13120; now < 20000; now += 20) {
        expired.detector.tick(now);
        expired.detector.sample(upright, still, now);
    }
    CHECK(alertCount() == 1);
    expired.detector.clearAlert(20000);
    CHECK(expired.detector.state() == FallState::MONITORING);
    expired.impact(20020);
    expired.hold(20040, 23040);
    expired.detector.tick(33040);
    CHECK(alertCount() == 2);

    Fixture sensorFailure;
    sensorFailure.pending();
    sensorFailure.detector.sample(upright, still, 3140);
    sensorFailure.detector.missingSample(4000);
    CHECK(sensorFailure.detector.state() == FallState::PENDING_CONFIRMATION);
    sensorFailure.detector.tick(13120);
    CHECK(sensorFailure.detector.state() == FallState::FALL_ALERT);
    CHECK(alertCount() == 1);

    Fixture reference;
    reference.pending();
    CHECK(!reference.detector.setReference(sideways, still));
    reference.detector.clearAlert(4000);
    CHECK(reference.detector.state() == FallState::PENDING_CONFIRMATION);
}

static void testInvalidDataAndReference() {
    FallDetector absent;
    absent.sample({0, 0, 4}, still, 100);
    CHECK(absent.state() == FallState::MONITORING);
    CHECK(!absent.setReference(still, still));
    CHECK(!absent.setReference({NAN, 0, 1}, still));
    CHECK(!absent.setReference(upright, {INFINITY, 0, 0}));

    const Vector3 bad[] = {{NAN, 0, 1}, {INFINITY, 0, 0}, {0, 0, 0}};
    for (const auto& value : bad) {
        Fixture f;
        f.impact();
        f.hold(120, 2120);
        f.detector.sample(value, still, 2140);
        f.hold(2160, 5100);
        CHECK(f.detector.state() == FallState::MONITORING);
    }
    Fixture badGyro;
    badGyro.impact();
    badGyro.hold(120, 2120);
    badGyro.detector.sample(sideways, {NAN, 0, 0}, 2140);
    badGyro.hold(2160, 5100);
    CHECK(badGyro.detector.state() == FallState::MONITORING);

    Fixture bias;
    CHECK(bias.detector.setReference(upright, {5, 0, 0}));
    bias.impact();
    bias.hold(120, 3120, sideways, {19, 0, 0});
    CHECK(bias.detector.state() == FallState::PENDING_CONFIRMATION);
}

static void testV1LimitsAndWearSignal() {
    Fixture noTrigger;
    noTrigger.hold(100, 10100);
    CHECK(noTrigger.detector.state() == FallState::MONITORING);

    Fixture headUpright;
    headUpright.impact();
    headUpright.hold(120, 5100, upright);
    CHECK(headUpright.detector.state() == FallState::MONITORING);

    // Deliberate fast lying/bending and an unworn dropped helmet can produce
    // the same IMU trace as a fall. V1 correctly documents this ambiguity.
    Fixture deliberate;
    deliberate.pending();
    deliberate.detector.confirm(4000);
    CHECK(deliberate.detector.state() == FallState::MONITORING);

    Fixture unworn;
    unworn.detector.sample({0, 0, 3}, still, 100, false);
    CHECK(unworn.detector.state() == FallState::MONITORING);
    unworn.impact(120);
    unworn.hold(140, 3140, sideways, still, false);
    CHECK(unworn.detector.state() == FallState::PENDING_CONFIRMATION);
}

static void testClockRollover() {
    Fixture f;
    const uint32_t start = UINT32_MAX - 1000;
    f.impact(start);
    f.hold(start + 20, start + 3020);
    CHECK(f.detector.state() == FallState::PENDING_CONFIRMATION);
    f.detector.tick(start + 13020);
    CHECK(f.detector.state() == FallState::FALL_ALERT);
    CHECK(alertCount() == 1);
}

static void checkPendingRoute(Fixture& fixture, FallRoute route) {
    CHECK(fixture.detector.state() == FallState::PENDING_CONFIRMATION);
    CHECK(!reports.empty());
    CHECK(reports.back().route == route);
}

static void startMovingPosture(Fixture& fixture) {
    fixture.impact();
    fixture.detector.sample(sideways, still, 120);
}

static void testV2FastAndSlowRoutes() {
    Fixture fast(FallConfig{});
    fast.pending();
    checkPendingRoute(fast, FallRoute::FAST_POSTURE);

    Fixture slow(FallConfig{});
    slow.hold(100, 8080);
    CHECK(slow.detector.state() == FallState::MONITORING);
    slow.detector.sample(sideways, still, 8100);
    checkPendingRoute(slow, FallRoute::SLOW_POSTURE);
    CHECK(reports.back().slowHeldMs == 8000);
    slow.detector.tick(18099);
    CHECK(slow.detector.state() == FallState::PENDING_CONFIRMATION);
    slow.detector.tick(18100);
    CHECK(slow.detector.state() == FallState::FALL_ALERT);
    CHECK(reports.back().route == FallRoute::SLOW_POSTURE);
    CHECK(alertCount() == 1);

    Fixture interrupted(FallConfig{});
    interrupted.hold(100, 5100);
    interrupted.detector.sample(upright, still, 5120);
    interrupted.hold(5140, 10140);
    CHECK(interrupted.detector.state() == FallState::MONITORING);

    Fixture failed(FallConfig{});
    failed.hold(100, 6100);
    failed.detector.missingSample(6120);
    failed.hold(6140, 14120);
    CHECK(failed.detector.state() == FallState::MONITORING);
    failed.detector.sample(sideways, still, 14140);
    checkPendingRoute(failed, FallRoute::SLOW_POSTURE);

    Fixture gap(FallConfig{});
    gap.hold(100, 6100);
    gap.detector.tick(6161);
    CHECK(!isfinite(gap.detector.tiltDegrees()));
    gap.hold(6180, 14160);
    CHECK(gap.detector.state() == FallState::MONITORING);
    gap.detector.sample(sideways, still, 14180);
    checkPendingRoute(gap, FallRoute::SLOW_POSTURE);

    // Slow posture timing continues after the six-second impact window closes.
    Fixture continued(FallConfig{});
    continued.impact();
    continued.hold(120, 3120, upright);
    continued.hold(3140, 11120);
    CHECK(continued.detector.state() == FallState::MONITORING);
    continued.detector.sample(sideways, still, 11140);
    checkPendingRoute(continued, FallRoute::SLOW_POSTURE);
    CHECK(reports.back().slowHeldMs == 8000);
}

static void testV2MovingRoute() {
    Fixture moving(FallConfig{});
    startMovingPosture(moving);
    // Rotation around gravity keeps the head tilted but prevents the stillness hold.
    moving.hold(140, 4100, sideways, {30, 0, 0});
    CHECK(moving.detector.state() == FallState::CHECKING_POSTURE);
    moving.detector.sample(sideways, {30, 0, 0}, 4120);
    checkPendingRoute(moving, FallRoute::MOVING_POSTURE);
    CHECK(reports.back().tiltedMs == 4000);
    moving.detector.sample(upright, still, 4140);
    moving.detector.missingSample(4160);
    CHECK(moving.detector.state() == FallState::PENDING_CONFIRMATION);
    moving.detector.confirm(5000);
    CHECK(moving.detector.state() == FallState::MONITORING);
    moving.detector.tick(20000);
    CHECK(alertCount() == 0);

    Fixture intermittent(FallConfig{});
    intermittent.impact();
    intermittent.hold(120, 2120);
    intermittent.hold(2140, 3120, upright);
    intermittent.hold(3140, 5140);
    checkPendingRoute(intermittent, FallRoute::MOVING_POSTURE);
    CHECK(reports.back().tiltedMs == 4000);

    Fixture exact(FallConfig{});
    exact.impact();
    exact.hold(120, 2080, upright);
    exact.detector.sample(sideways, still, 2100);
    exact.hold(2120, 6080, sideways, {30, 0, 0});
    CHECK(exact.detector.state() == FallState::CHECKING_POSTURE);
    exact.detector.tick(6100);
    exact.detector.sample(sideways, {30, 0, 0}, 6100);
    checkPendingRoute(exact, FallRoute::MOVING_POSTURE);

    Fixture late(FallConfig{});
    late.impact();
    late.hold(120, 2100, upright);
    late.detector.sample(sideways, still, 2120);
    late.hold(2140, 6120, sideways, {30, 0, 0});
    CHECK(late.detector.state() == FallState::MONITORING);

    Fixture deadline(FallConfig{});
    deadline.impact();
    deadline.hold(120, 4080, upright);
    deadline.detector.sample({0, 0, 3}, still, 4100);
    deadline.detector.tick(6101);
    CHECK(deadline.detector.state() == FallState::MONITORING);
}

static void testV2MovingFaults() {
    Fixture fault(FallConfig{});
    startMovingPosture(fault);
    fault.hold(140, 2120, sideways, {30, 0, 0});
    fault.detector.missingSample(2140);
    fault.detector.sample(sideways, still, 2160);
    fault.hold(2180, 6100, sideways, {30, 0, 0});
    CHECK(fault.detector.state() == FallState::MONITORING);
    CHECK(alertCount() == 0);

    Fixture gap(FallConfig{});
    startMovingPosture(gap);
    gap.hold(140, 2120, sideways, {30, 0, 0});
    gap.detector.sample(sideways, still, 2200);
    gap.hold(2220, 6100, sideways, {30, 0, 0});
    CHECK(gap.detector.state() == FallState::MONITORING);

    Fixture invalid(FallConfig{});
    startMovingPosture(invalid);
    invalid.hold(140, 2120, sideways, {30, 0, 0});
    invalid.detector.sample({NAN, 0, 0}, still, 2140);
    invalid.detector.sample(sideways, still, 2160);
    invalid.hold(2180, 6100, sideways, {30, 0, 0});
    CHECK(invalid.detector.state() == FallState::MONITORING);

    Fixture timeout(FallConfig{});
    startMovingPosture(timeout);
    timeout.hold(140, 4120, sideways, {30, 0, 0});
    checkPendingRoute(timeout, FallRoute::MOVING_POSTURE);
    timeout.detector.missingSample(5000);
    timeout.detector.confirm(14120);
    CHECK(timeout.detector.state() == FallState::FALL_ALERT);
    for (uint32_t now = 14120; now < 16000; now += 20) timeout.detector.tick(now);
    CHECK(alertCount() == 1);
}

static void testV2DailyActivitiesAndLimits() {
    const Vector3 directions[] = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
    for (const auto& direction : directions) {
        Fixture fall(FallConfig{});
        fall.impact();
        fall.hold(120, 3120, direction);
        checkPendingRoute(fall, FallRoute::FAST_POSTURE);
    }
    Fixture sitting(FallConfig{});
    sitting.impact();
    sitting.hold(120, 12120, upright);
    CHECK(sitting.detector.state() == FallState::MONITORING);

    Fixture shake(FallConfig{});
    shake.detector.sample(upright, still, 80);
    shake.detector.sample(upright, {0, 0, 230}, 100);
    shake.hold(120, 12120, upright);
    CHECK(shake.detector.state() == FallState::MONITORING);

    const Vector3 mildTilt{0.5f, 0, 0.8660254f};
    Fixture standing(FallConfig{});
    standing.hold(100, 12100, mildTilt);
    CHECK(standing.detector.state() == FallState::MONITORING);

    Fixture bend(FallConfig{});
    bend.hold(100, 6100);
    bend.hold(6120, 12120, upright);
    CHECK(bend.detector.state() == FallState::MONITORING);

    // A long deliberate bend/lean and a slow fall can be indistinguishable.
    Fixture prolongedBend(FallConfig{});
    prolongedBend.hold(100, 8100);
    checkPendingRoute(prolongedBend, FallRoute::SLOW_POSTURE);
    prolongedBend.detector.confirm(9000);
    CHECK(prolongedBend.detector.state() == FallState::MONITORING);
    prolongedBend.hold(9020, 10020);
    CHECK(prolongedBend.detector.state() == FallState::MONITORING);

    // A wall-supported fall with an upright head remains a known blind spot.
    Fixture wall(FallConfig{});
    wall.impact();
    wall.hold(120, 12120, upright);
    CHECK(wall.detector.state() == FallState::MONITORING);

    Fixture unworn(FallConfig{});
    unworn.hold(100, 12100, sideways, still, false);
    CHECK(unworn.detector.state() == FallState::MONITORING);
    unworn.hold(12120, 20120, sideways, still, true);
    checkPendingRoute(unworn, FallRoute::SLOW_POSTURE);

    Fixture lostWearSignal(FallConfig{});
    lostWearSignal.hold(100, 4100);
    lostWearSignal.hold(4120, 8100, sideways, still, false);
    checkPendingRoute(lostWearSignal, FallRoute::SLOW_POSTURE);

    Fixture reference(FallConfig{});
    reference.hold(100, 6100);
    CHECK(reference.detector.setReference(sideways, still));
    reference.hold(6120, 16120);
    CHECK(reference.detector.state() == FallState::MONITORING);
    CHECK(reference.detector.tiltDegrees() < 0.1f);
}

static void testV2Orientation() {
    Fixture rotation(FallConfig{});
    rotation.detector.sample(upright, still, 100);
    for (uint32_t elapsed = 20; elapsed <= 1000; elapsed += 20) {
        // Non-gravitational acceleration is rejected for tilt correction;
        // gyro alone predicts a 90-degree rotation over one second.
        rotation.detector.sample({0, 0, 1.8f}, {0, -90, 0}, 100 + elapsed);
    }
    CHECK(fabsf(rotation.detector.tiltDegrees() - 90) < 0.1f);
    CHECK(rotation.detector.state() == FallState::MONITORING);
    rotation.detector.sample(sideways, still, 1120);
    CHECK(fabsf(rotation.detector.tiltDegrees() - 90) < 0.1f);

    Fixture acceleration(FallConfig{});
    acceleration.detector.sample(upright, still, 80);
    acceleration.detector.sample({3, 0, 0}, still, 100);
    acceleration.hold(120, 6100, {1.8f, 0, 0});
    CHECK(acceleration.detector.state() == FallState::MONITORING);
    CHECK(acceleration.detector.tiltDegrees() < 0.1f);

    Fixture gap(FallConfig{});
    gap.detector.sample(upright, still, 100);
    gap.detector.sample({0, 0, 1.8f}, {0, -90, 0}, 180);
    CHECK(!isfinite(gap.detector.tiltDegrees()));
    gap.detector.sample(sideways, still, 200);
    CHECK(fabsf(gap.detector.tiltDegrees() - 90) < 0.1f);

    Fixture gradual(FallConfig{});
    gradual.detector.sample(upright, still, 100);
    for (uint32_t elapsed = 20; elapsed <= 2000; elapsed += 20) {
        const float radians = 35 * (elapsed * 0.001f) * 0.0174532925f;
        gradual.detector.sample({sinf(radians), 0, cosf(radians)}, {0, -35, 0}, 100 + elapsed);
    }
    CHECK(fabsf(gradual.detector.tiltDegrees() - 70) < 1);
    CHECK(gradual.detector.state() == FallState::MONITORING);
    const Vector3 seventyDegrees{0.9396926f, 0, 0.3420201f};
    gradual.hold(2120, 10100, seventyDegrees);
    CHECK(gradual.detector.state() == FallState::MONITORING);
    gradual.detector.sample(seventyDegrees, still, 10120);
    checkPendingRoute(gradual, FallRoute::SLOW_POSTURE);
}

static void testSixtyDegreeThreshold() {
    CHECK(FallConfig{}.tiltDegrees == 60.0f);
    const float angles[] = {55.0f, 59.9f, 60.1f};
    for (float degrees : angles) {
        const float radians = degrees * 0.0174532925f;
        const Vector3 pose{sinf(radians), 0, cosf(radians)};
        const bool qualifies = degrees > 60.0f;
        Fixture fast(FallConfig{});
        fast.impact();
        fast.hold(120, 3120, pose);
        if (qualifies) checkPendingRoute(fast, FallRoute::FAST_POSTURE);
        else CHECK(fast.detector.state() == FallState::CHECKING_POSTURE);

        Fixture slow(FallConfig{});
        slow.hold(100, 10100, pose);
        if (qualifies) checkPendingRoute(slow, FallRoute::SLOW_POSTURE);
        else CHECK(slow.detector.state() == FallState::MONITORING);

        Fixture moving(FallConfig{});
        moving.impact();
        moving.detector.sample(pose, still, 120);
        // Rotate around gravity so the tilt remains fixed while moving.
        const Vector3 gyro{pose.x * 30, pose.y * 30, pose.z * 30};
        moving.hold(140, 4120, pose, gyro);
        if (qualifies) checkPendingRoute(moving, FallRoute::MOVING_POSTURE);
        else CHECK(moving.detector.state() == FallState::CHECKING_POSTURE);
    }
}

static void testV2RolloverAndRearming() {
    const uint32_t start = UINT32_MAX - 4000;
    Fixture slow(FallConfig{});
    slow.hold(start, start + 8000);
    checkPendingRoute(slow, FallRoute::SLOW_POSTURE);
    slow.detector.tick(start + 18000);
    CHECK(slow.detector.state() == FallState::FALL_ALERT);
    slow.detector.clearAlert(start + 18020);
    slow.hold(start + 18040, start + 26020);
    CHECK(slow.detector.state() == FallState::MONITORING);
    slow.detector.sample(sideways, still, start + 26040);
    checkPendingRoute(slow, FallRoute::SLOW_POSTURE);

    Fixture moving(FallConfig{});
    moving.impact(start);
    moving.detector.sample(sideways, still, start + 20);
    moving.hold(start + 40, start + 4020, sideways, {30, 0, 0});
    checkPendingRoute(moving, FallRoute::MOVING_POSTURE);
}

int runFallAlarmTests();
int runPushButtonTests();

int main() {
    testTriggersAndDirections();
    testTimeBoundaries();
    testInterruptedPosture();
    testConfirmationAndAlert();
    testInvalidDataAndReference();
    testV1LimitsAndWearSignal();
    testClockRollover();
    testV2FastAndSlowRoutes();
    testV2MovingRoute();
    testV2MovingFaults();
    testV2DailyActivitiesAndLimits();
    testV2Orientation();
    testV2RolloverAndRearming();
    testSixtyDegreeThreshold();
    checks += runFallAlarmTests();
    checks += runPushButtonTests();
    printf("PASS: 26 test groups, %d checks, simulated timestamps, no hardware required.\n", checks);
}
