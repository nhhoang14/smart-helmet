#include <Arduino.h>
#include <Wire.h>
#include <math.h>
#include <Preferences.h>
#include "fall_detector.h"
#include "fall_alarm.h"
#include "push_button.h"
#include "config.h"
#include "mq135_sensor.h"
#include "alarm_policy.h"
#include "helmet_services.h"
#include "gas_policy.h"

// The latest Nshop reference is a passive buzzer, driven with hardware PWM.
// Set BUZZER_IS_PASSIVE to false for the earlier TMB12A05 active-buzzer model.
constexpr int BUZZER_CONTROL_PIN = PIN_BUZZER;
constexpr bool BUZZER_IS_PASSIVE = HELMET_BUZZER_PASSIVE;
constexpr uint8_t BUZZER_PWM_CHANNEL = 0;
constexpr uint32_t BUZZER_TONE_HZ = 2500;
constexpr uint8_t BUZZER_PWM_BITS = 10;
constexpr uint32_t BUZZER_PWM_DUTY = 1u << (BUZZER_PWM_BITS - 1);
bool buzzerReady = false;
bool buzzerActive = false;
constexpr int CONFIRM_BUTTON_PIN = PIN_CONFIRM_BUTTON;
helmet::PushButton confirmButton;

void writeBuzzer(bool active) {
    buzzerActive = active;
    if (!buzzerReady) return;
    if (BUZZER_IS_PASSIVE) ledcWrite(BUZZER_PWM_CHANNEL, active ? BUZZER_PWM_DUTY : 0);
    else digitalWrite(BUZZER_CONTROL_PIN, active ? HIGH : LOW);
}

helmet::FallAlarm fallAlarm(writeBuzzer);

void reportFall(const helmet::FallReport& report);
helmet::FallDetector fallDetector(reportFall);
helmet::AlarmPolicy alarmPolicy;
MQ135Sensor gasSensor(PIN_MQ135_ANALOG, PIN_MQ135_DIGITAL);
helmet::PushButton sosButton;
SystemState gasState = STATE_WARMING_UP;
helmet::GasPolicy gasPolicy(GAS_WARN_PPM_DEFAULT, GAS_DANGER_PPM_DEFAULT,
                           ADC_THRESHOLD_WARNING, ADC_THRESHOLD_DANGER,
                           GAS_HYSTERESIS_PPM, ADC_HYSTERESIS);
bool worn = false;
bool sosActive = false;
bool remoteActive = false;
bool fallRearmRequired = false;
uint32_t remoteStartedAt = 0;
float lastAccelerationG = NAN;
float gasWarnThreshold = GAS_WARN_PPM_DEFAULT;
float gasDangerThreshold = GAS_DANGER_PPM_DEFAULT;
float pendingImpactThreshold = NAN;
void refreshAlarm(uint32_t now);
void acknowledgeAlarm(uint32_t now);
void updateIntegratedServices(uint32_t now);
bool sensorFault = false;
helmet::FallState lastReportedFallState = helmet::FallState::MONITORING;

void reportFall(const helmet::FallReport& report) {
    refreshAlarm(report.timestamp);
    Serial.printf("FALL t=%lu state=%s a=%.3f g gyro=%.2f dps tilt=%.2f deg "
                  "held=%lu ms slow=%lu ms moving=%lu ms route=%s reason=%s\n",
                  static_cast<unsigned long>(report.timestamp), helmet::fallStateName(report.state),
                  report.accelerationG, report.rotationDps, report.tiltDegrees,
                  static_cast<unsigned long>(report.heldMs),
                  static_cast<unsigned long>(report.slowHeldMs),
                  static_cast<unsigned long>(report.tiltedMs),
                  helmet::fallRouteName(report.route), report.reason);
    if (report.state == helmet::FallState::PENDING_CONFIRMATION &&
        lastReportedFallState != report.state) {
        Serial.println("BUZZER: reminder 150 ms ON / 1350 ms OFF. Press D27 button or send k within 10 s.");
    }
    if (report.state == helmet::FallState::FALL_ALERT && lastReportedFallState != report.state) {
        queueHelmetEvent("FALL", "Nghi ngo te nga, khong phan hoi", report.accelerationG);
        Serial.println("FALL_ALERT: press D27 or send r to acknowledge.");
    }
    if (report.state == helmet::FallState::MONITORING &&
        (lastReportedFallState == helmet::FallState::PENDING_CONFIRMATION ||
         lastReportedFallState == helmet::FallState::FALL_ALERT)) {
        Serial.println("FALL: acknowledged; waiting for upright posture before rearming.");
    }
    lastReportedFallState = report.state;
}

constexpr int I2C_SDA_PIN = PIN_I2C_SDA;
constexpr int I2C_SCL_PIN = PIN_I2C_SCL;
constexpr unsigned long SAMPLE_INTERVAL_MS = 20;  // Đọc 50 lần/giây
constexpr unsigned long PRINT_INTERVAL_MS = 500;  // In 2 lần/giây
constexpr uint8_t REG_SMPLRT_DIV   = 0x19;
constexpr uint8_t REG_CONFIG       = 0x1A;
constexpr uint8_t REG_GYRO_CONFIG  = 0x1B;
constexpr uint8_t REG_ACCEL_CONFIG = 0x1C;
constexpr uint8_t REG_ACCEL_XOUT_H = 0x3B;
constexpr uint8_t REG_PWR_MGMT_1   = 0x6B;
constexpr uint8_t REG_WHO_AM_I     = 0x75;

constexpr uint8_t WHO_AM_I_MPU6050 = 0x68;
constexpr uint8_t PWR_MGMT_1_SLEEP = 0x40;   // Bị bật lại sau mỗi lần reset nguồn
constexpr unsigned long RETRY_INTERVAL_MS = 2000;
constexpr uint16_t STALL_SAMPLES = 500;  // 500 mẫu × 20 ms = 10 giây
constexpr uint8_t ACCEL_CONFIG_VALUE = 0x10;  // ±8 g
constexpr uint8_t GYRO_CONFIG_VALUE = 0x10;   // ±1000 độ/giây
constexpr float ACCEL_LSB_PER_G = 4096.0f;
constexpr float GYRO_LSB_PER_DPS = 32.8f;
constexpr uint8_t MAX_CONSECUTIVE_READ_FAILURES = 3;

const uint8_t CANDIDATE_ADDRESSES[] = {0x68, 0x69};
uint8_t sensorAddress = 0;
uint8_t lastI2cError = 0;
uint8_t lastReadBytes = 0;

struct Reading {
    float ax, ay, az;
    float gx, gy, gz;
    float temperature;
};

void resetFallDetector(const char* reason);

bool writeRegister(uint8_t address, uint8_t reg, uint8_t value) {
    Wire.beginTransmission(address);
    Wire.write(reg);
    Wire.write(value);
    return Wire.endTransmission() == 0;
}

bool readRegisters(uint8_t address, uint8_t reg,
                   uint8_t* buffer, uint8_t length) {
    lastReadBytes = 0;
    Wire.beginTransmission(address);
    Wire.write(reg);

    lastI2cError = Wire.endTransmission(false);
    if (lastI2cError != 0) {
        return false;
    }

    lastReadBytes = Wire.requestFrom(address, length);
    if (lastReadBytes != length) {
        while (Wire.available()) {
            Wire.read();
        }
        return false;
    }

    for (uint8_t i = 0; i < length; ++i) {
        if (!Wire.available()) {
            return false;
        }
        buffer[i] = static_cast<uint8_t>(Wire.read());
    }

    return true;
}

bool readRegister(uint8_t address, uint8_t reg, uint8_t& value) {
    return readRegisters(address, reg, &value, 1);
}

bool configureSensor() {
    return writeRegister(sensorAddress, REG_PWR_MGMT_1, 0x01) &&
           writeRegister(sensorAddress, REG_SMPLRT_DIV, 19) &&  // 1 kHz / 20 = 50 Hz
           writeRegister(sensorAddress, REG_CONFIG, 0x03) &&
           writeRegister(sensorAddress, REG_GYRO_CONFIG, GYRO_CONFIG_VALUE) &&
           writeRegister(sensorAddress, REG_ACCEL_CONFIG, ACCEL_CONFIG_VALUE);
}

void restartBus() {
    Wire.end();
    pinMode(I2C_SDA_PIN, INPUT_PULLUP);
    pinMode(I2C_SCL_PIN, INPUT_PULLUP);
    delay(10);
    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN, 100000);
    Wire.setTimeOut(20);
}

void scanBus() {
    Serial.println("Scanning I2C bus...");
    unsigned int deviceCount = 0;

    for (uint8_t address = 0x08; address <= 0x77; ++address) {
        Wire.beginTransmission(address);
        uint8_t error = Wire.endTransmission();

        if (error == 0) {
            Serial.printf("  device at 0x%02X\n", address);
            ++deviceCount;
        }
    }

    if (deviceCount == 0) {
        Serial.println("  no devices found.");
    } else {
        Serial.printf("Scan done: %u device(s).\n", deviceCount);
    }
}

uint8_t findSensor() {
    for (uint8_t address : CANDIDATE_ADDRESSES) {
        Wire.beginTransmission(address);
        if (Wire.endTransmission() == 0) {
            return address;
        }
    }
    return 0;
}

// Kiểm tra cảm biến còn sống và còn thức; nếu không thì kết nối lại.
// force = true bỏ qua bước kiểm tra nhanh, cấu hình lại từ đầu.
bool ensureSensor(bool force) {
    static uint32_t lastAttempt = 0;
    static bool lastAttemptFailed = false;

    // Recovery cancels acquisition but preserves the saved mounting reference.
    resetFallDetector("sensor recovery");

    if (lastAttemptFailed && millis() - lastAttempt < RETRY_INTERVAL_MS) {
        return false;
    }

    if (!force && sensorAddress != 0) {
        uint8_t whoAmI = 0;
        uint8_t power = 0;
        if (readRegister(sensorAddress, REG_WHO_AM_I, whoAmI) &&
            whoAmI == WHO_AM_I_MPU6050 &&
            readRegister(sensorAddress, REG_PWR_MGMT_1, power) &&
            (power & PWR_MGMT_1_SLEEP) == 0) {
            lastAttemptFailed = false;
            return true;
        }
    }

    lastAttempt = millis();
    Serial.println("Recovery: reconnecting MPU6050...");

    if (force) {
        // The discovery path below restarts the bus before probing addresses.
        sensorAddress = 0;
    }

    uint8_t power = 0;
    if (sensorAddress != 0) {
        if (readRegister(sensorAddress, REG_PWR_MGMT_1, power)) {
            Serial.printf("  PWR_MGMT_1 = 0x%02X before re-init\n", power);
        } else {
            Serial.println("  sensor not answering -> restarting I2C bus");
            sensorAddress = 0;
        }
    }

    if (sensorAddress == 0) {
        restartBus();
        sensorAddress = findSensor();
        if (sensorAddress == 0) {
            Serial.println("  no device at 0x68/0x69, will retry");
            lastAttemptFailed = true;
            return false;
        }
    }

    uint8_t whoAmI = 0;
    if (!readRegister(sensorAddress, REG_WHO_AM_I, whoAmI) ||
        whoAmI != WHO_AM_I_MPU6050) {
        Serial.printf("  unexpected WHO_AM_I = 0x%02X\n", whoAmI);
        sensorAddress = 0;
        lastAttemptFailed = true;
        return false;
    }

    if (!configureSensor()) {
        Serial.println("  register configuration failed");
        sensorAddress = 0;
        lastAttemptFailed = true;
        return false;
    }

    delay(100);
    Serial.println("  sensor ready.");
    lastAttemptFailed = false;
    return true;
}

bool readReading(Reading& reading) {
    uint8_t raw[14];

    if (!readRegisters(sensorAddress, REG_ACCEL_XOUT_H,
                       raw, sizeof(raw))) {
        return false;
    }

    // Nếu cả 14 byte đều bằng 0 thì đây không phải mẫu đo đáng tin cậy.
    bool allZero = true;
    for (uint8_t value : raw) {
        if (value != 0) {
            allZero = false;
            break;
        }
    }

    if (allZero) {
        Serial.println("Invalid MPU6050 sample: all 14 bytes are zero.");
        return false;
    }

    int16_t accelX = static_cast<int16_t>((raw[0] << 8) | raw[1]);
    int16_t accelY = static_cast<int16_t>((raw[2] << 8) | raw[3]);
    int16_t accelZ = static_cast<int16_t>((raw[4] << 8) | raw[5]);
    int16_t rawTemp = static_cast<int16_t>((raw[6] << 8) | raw[7]);
    int16_t gyroX  = static_cast<int16_t>((raw[8] << 8) | raw[9]);
    int16_t gyroY  = static_cast<int16_t>((raw[10] << 8) | raw[11]);
    int16_t gyroZ  = static_cast<int16_t>((raw[12] << 8) | raw[13]);

    reading.ax = accelX / ACCEL_LSB_PER_G;
    reading.ay = accelY / ACCEL_LSB_PER_G;
    reading.az = accelZ / ACCEL_LSB_PER_G;

    reading.gx = gyroX / GYRO_LSB_PER_DPS;
    reading.gy = gyroY / GYRO_LSB_PER_DPS;
    reading.gz = gyroZ / GYRO_LSB_PER_DPS;

    reading.temperature = rawTemp / 340.0f + 36.53f;
    return true;
}

float magnitude(float x, float y, float z) {
    return sqrtf(x * x + y * y + z * z);
}


// Calibration gates are acquisition checks, separate from fall thresholds.
struct Vec3 { float x, y, z; };
struct StoredPosture {
    uint32_t magic;
    uint32_t version;
    Vec3 gravity;
    Vec3 gyroBias;
};
constexpr uint32_t POSTURE_MAGIC = 0x484D5531;
constexpr uint32_t POSTURE_VERSION = 1;
constexpr uint16_t CAL_SAMPLES = 101; // 101 samples span 2 s at 50 Hz
StoredPosture posture{};
bool hasPosture = false;

void applyFallReference() {
    fallDetector.setReference({posture.gravity.x, posture.gravity.y, posture.gravity.z},
                             {posture.gyroBias.x, posture.gyroBias.y, posture.gyroBias.z});
}
struct Calibration {
    bool countdown = false;
    bool collecting = false;
    uint32_t requested = 0, first = 0, last = 0;
    uint16_t count = 0;
    Vec3 anchor{}, meanA{}, meanG{};
    float m2 = 0;
} calibration;

float norm(const Vec3& v) { return magnitude(v.x, v.y, v.z); }
float vectorAngle(const Vec3& a, const Vec3& b) {
    float n = norm(a) * norm(b);
    if (n < 0.01f) return NAN;
    float c = (a.x*b.x + a.y*b.y + a.z*b.z) / n;
    return acosf(fmaxf(-1.0f, fminf(1.0f, c))) * 180.0f / PI;
}
bool validPosture(const StoredPosture& s) {
    const float n = norm(s.gravity), g = norm(s.gyroBias);
    return s.magic == POSTURE_MAGIC && s.version == POSTURE_VERSION &&
           isfinite(n) && n >= 0.8f && n <= 1.2f &&
           isfinite(g) && g < 10.0f;
}
void printPosture() {
    if (!hasPosture) { Serial.println("NO_REFERENCE: send c to calibrate."); return; }
    Serial.printf("REFERENCE[g] %.5f %.5f %.5f | gyro_bias[dps] %.4f %.4f %.4f\n",
                  posture.gravity.x, posture.gravity.y, posture.gravity.z,
                  posture.gyroBias.x, posture.gyroBias.y, posture.gyroBias.z);
}
void loadPosture() {
    Preferences prefs;
    if (!prefs.begin("helmet-pose", true)) {
        Serial.println("NO_REFERENCE: storage unavailable or first use; send c.");
        return;
    }
    StoredPosture saved{};
    bool ok = prefs.getBytesLength("posture") == sizeof(saved) &&
              prefs.getBytes("posture", &saved, sizeof(saved)) == sizeof(saved);
    prefs.end();
    if (ok && validPosture(saved)) {
        posture = saved; hasPosture = true;
        applyFallReference();
        Serial.println("REFERENCE_LOADED: saved posture restored."); printPosture();
    } else Serial.println("NO_REFERENCE: send c to calibrate.");
}
bool savePosture(const StoredPosture& value) {
    Preferences prefs;
    if (!prefs.begin("helmet-pose", false)) return false;
    bool ok = prefs.putBytes("posture", &value, sizeof(value)) == sizeof(value);
    StoredPosture check{};
    ok = ok && prefs.getBytes("posture", &check, sizeof(check)) == sizeof(check) &&
         memcmp(&value, &check, sizeof(value)) == 0;
    prefs.end();
    return ok;
}
void abortCalibration(const char* reason) {
    if (calibration.countdown || calibration.collecting)
        Serial.printf("CAL_ABORT: %s. Previous reference kept; send c to retry.\n", reason);
    calibration = {};
}
// Existing sensor error paths call this hook. Never erase the saved reference.
void resetFallDetector(const char* reason) {
    lastAccelerationG = NAN;
    abortCalibration(reason);
    fallDetector.missingSample(millis(), reason);
}
void handleConfirmButton(uint32_t now) {
    const bool pressed = digitalRead(CONFIRM_BUTTON_PIN) == LOW;
    static bool previousRaw = pressed;
    if (pressed != previousRaw) {
        previousRaw = pressed;
        Serial.printf("BUTTON_RAW: GPIO%d=%s\n", CONFIRM_BUTTON_PIN, pressed ? "LOW" : "HIGH");
    }
    const helmet::ButtonEvent event = confirmButton.update(pressed, now);
    if (event == helmet::ButtonEvent::NONE) return;
    Serial.printf("BUTTON: %s GPIO%d state=%s\n",
                  event == helmet::ButtonEvent::PRESSED ? "PRESSED" : "RELEASED",
                  CONFIRM_BUTTON_PIN, helmet::fallStateName(fallDetector.state()));
    if (event != helmet::ButtonEvent::PRESSED) return;
    acknowledgeAlarm(now);
}
void handleCommands() {
    while (Serial.available()) {
        const char c = static_cast<char>(Serial.read());
        if (c == 'c' || c == 'C') {
            if (fallDetector.state() != helmet::FallState::MONITORING) {
                Serial.println("CAL_BUSY: finish the current fall event before recalibrating.");
                continue;
            }
            calibration = {};
            calibration.countdown = true;
            calibration.requested = millis();
            Serial.println("CAL_COUNTDOWN: 3 s. Wear helmet normally, look straight, keep still.");
        } else if (c == 'p' || c == 'P') printPosture();
        else if (c == 'x' || c == 'X') abortCalibration("cancelled by user");
        else if (c == 'k' || c == 'K' || c == 'r' || c == 'R') acknowledgeAlarm(millis());
        else if (c == 's' || c == 'S') {
            sosActive = true;
            queueHelmetEvent("SOS", "SOS activated by user");
        }
        else if (c == 'b' || c == 'B') {
            if (fallAlarm.testBeep(millis())) Serial.println("BUZZER: manual test 200 ms.");
            else Serial.println("BUZZER: finish the current fall event before a manual test.");
        }
    }
}
void updateCalibration(const Reading& r, uint32_t now) {
    if (calibration.countdown) {
        if (now - calibration.requested < 3000) return;
        calibration.countdown = false;
        calibration.collecting = true;
        Serial.println("CAL_COLLECTING: keep head still for 2 s.");
    }
    if (!calibration.collecting) return;
    const Vec3 a{r.ax, r.ay, r.az}, g{r.gx, r.gy, r.gz};
    const float an = norm(a), gn = norm(g);
    if (!isfinite(an) || !isfinite(gn) || an < 0.8f || an > 1.2f || gn >= 10.0f) {
        abortCalibration("movement or invalid sample"); return;
    }
    if (calibration.count == 0) {
        calibration.first = now;
        calibration.anchor = a;
    } else if (now - calibration.last > 3 * SAMPLE_INTERVAL_MS) {
        abortCalibration("sampling gap"); return;
    }
    // Also reject slow head tilting: |a| near 1 g alone cannot prove stillness.
    if (vectorAngle(calibration.anchor, a) > 3.0f) {
        abortCalibration("head orientation changed"); return;
    }
    calibration.last = now;
    ++calibration.count;
    const float n = calibration.count;
    const Vec3 d{a.x-calibration.meanA.x, a.y-calibration.meanA.y, a.z-calibration.meanA.z};
    calibration.meanA.x += d.x/n; calibration.meanA.y += d.y/n; calibration.meanA.z += d.z/n;
    calibration.m2 += d.x*(a.x-calibration.meanA.x) + d.y*(a.y-calibration.meanA.y) + d.z*(a.z-calibration.meanA.z);
    calibration.meanG.x += (g.x-calibration.meanG.x)/n;
    calibration.meanG.y += (g.y-calibration.meanG.y)/n;
    calibration.meanG.z += (g.z-calibration.meanG.z)/n;
    if (calibration.count < CAL_SAMPLES) return;
    const float rms = sqrtf(fmaxf(0.0f, calibration.m2/n));
    if (now - calibration.first < 1980 || rms > 0.03f) {
        abortCalibration("insufficient duration or excessive vibration"); return;
    }
    StoredPosture candidate{};
    candidate.magic = POSTURE_MAGIC; candidate.version = POSTURE_VERSION;
    candidate.gravity = calibration.meanA; candidate.gyroBias = calibration.meanG;
    if (!validPosture(candidate) || !savePosture(candidate)) {
        abortCalibration("validation or storage failure"); return;
    }
    posture = candidate; hasPosture = true;
    applyFallReference();
    Serial.printf("CAL_OK: samples=%u duration=%lu ms rms=%.5f g; reference saved.\n",
                  calibration.count, static_cast<unsigned long>(now-calibration.first), rms);
    calibration = {};
    printPosture();
}
void printPostureAngle() {
    if (!hasPosture) { Serial.println(" | tilt=NA (send c)"); return; }
    if (calibration.countdown || calibration.collecting) {
        Serial.println(" | tilt=NA (calibrating)");
        return;
    }
    const float tilt = fallDetector.tiltDegrees();
    if (isfinite(tilt)) Serial.printf(" | tilt=%.2f deg (fused)\n", tilt);
    else Serial.println(" | tilt=NA (awaiting valid quasi-static sample)");
}

uint8_t activeAlarmSources() {
    uint8_t sources = 0;
    const auto state = fallDetector.state();
    if (state == helmet::FallState::PENDING_CONFIRMATION || state == helmet::FallState::FALL_ALERT)
        sources |= helmet::AlarmPolicy::FALL;
    if (gasState == STATE_WARNING) sources |= helmet::AlarmPolicy::GAS_WARNING;
    if (gasState == STATE_DANGER) sources |= helmet::AlarmPolicy::GAS_DANGER;
    if (sosActive) sources |= helmet::AlarmPolicy::SOS;
    if (remoteActive) sources |= helmet::AlarmPolicy::REMOTE;
    return sources;
}
void refreshAlarm(uint32_t now) {
    alarmPolicy.update(activeAlarmSources());
    fallAlarm.update(alarmPolicy.soundState(fallDetector.state() == helmet::FallState::PENDING_CONFIRMATION), now);
}
void acknowledgeAlarm(uint32_t now) {
    alarmPolicy.update(activeAlarmSources());
    alarmPolicy.acknowledge();
    const auto state = fallDetector.state();
    if (state == helmet::FallState::PENDING_CONFIRMATION || state == helmet::FallState::FALL_ALERT) {
        queueHelmetEvent("FALL_ACK", "Wearer confirmed OK using the acknowledgement button");
        fallRearmRequired = true;
        if (state == helmet::FallState::PENDING_CONFIRMATION) fallDetector.confirm(now);
        else fallDetector.clearAlert(now);
    }
    sosActive = false;
    remoteActive = false;
    // Also cancel a manual beep even if no fall or gas source is active.
    fallAlarm.begin();
    refreshAlarm(now);
    Serial.println("BUTTON: acknowledged, buzzer stopped. Gas stays muted until it clears or escalates.");
}
void updateIntegratedServices(uint32_t now) {
    if (HELMET_ENABLE_WEAR) {
        static bool raw = worn;
        static uint32_t changedAt = now;
        const bool reading = digitalRead(PIN_TOUCH_WORN) == LOW;
        if (reading != raw) { raw = reading; changedAt = now; }
        if (raw != worn && now - changedAt >= 50) {
            worn = raw;
            Serial.printf("WEAR: %s\n", worn ? "WORN" : "REMOVED");
        }
    }
    if (HELMET_ENABLE_SOS && sosButton.update(digitalRead(PIN_SOS_BUTTON) == LOW, now) == helmet::ButtonEvent::PRESSED) {
        sosActive = true;
        queueHelmetEvent("SOS", "Dedicated SOS button pressed");
    }
    if (HELMET_ENABLE_GAS) {
        gasSensor.update();
        {
            const auto previous = gasState;
            const float ppm = gasSensor.getPpm(), adc = gasSensor.getFilteredADC();
            const auto level = gasPolicy.update(ppm, adc, gasSensor.isWarmedUp(),
                                                !HELMET_ENABLE_WEAR || worn, gasSensor.isDigitalTriggered());
            switch (level) {
                case helmet::GasLevel::WARMING_UP: gasState = STATE_WARMING_UP; break;
                case helmet::GasLevel::SAFE: gasState = STATE_SAFE; break;
                case helmet::GasLevel::WARNING: gasState = STATE_WARNING; break;
                case helmet::GasLevel::DANGER: gasState = STATE_DANGER; break;
            }
            if (previous != gasState) {
                if (gasState == STATE_DANGER) queueHelmetEvent("GAS_DANGER", "Gas danger threshold exceeded", ppm);
                else if (gasState == STATE_WARNING) queueHelmetEvent("GAS_WARNING", "Gas warning threshold exceeded", ppm);
                Serial.printf("GAS_STATE: %d ppm=%.1f adc=%.0f\n", gasState, ppm, adc);
            }
        }
    }
    HelmetCommand command;
    // Bound work per loop even if a remote publisher floods the command queue.
    for (uint8_t count = 0; count < 8 && takeHelmetCommand(command); ++count) {
        if (command.type == HelmetCommandType::MUTE) acknowledgeAlarm(now);
        else if (command.type == HelmetCommandType::FIND_ON) { remoteActive = true; remoteStartedAt = now; }
        else if (command.type == HelmetCommandType::FIND_OFF) remoteActive = false;
        else {
            if (gasPolicy.setThresholds(command.gasWarn, command.gasDanger)) {
                gasWarnThreshold = command.gasWarn;
                gasDangerThreshold = command.gasDanger;
            }
            if (isfinite(command.impactG) && command.impactG > 1.15f && command.impactG <= 16) pendingImpactThreshold = command.impactG;
            Serial.printf("THRESHOLDS: gas_warn=%.1f gas_danger=%.1f impact_request=%.2f\n",
                          gasWarnThreshold, gasDangerThreshold, pendingImpactThreshold);
        }
    }
    if (isfinite(pendingImpactThreshold) && fallDetector.setImpactThreshold(pendingImpactThreshold)) pendingImpactThreshold = NAN;
    if (remoteActive && now - remoteStartedAt >= 10000) remoteActive = false;
    const float tilt = fallDetector.tiltDegrees();
    if (fallRearmRequired && isfinite(tilt) && tilt <= helmet::FallConfig{}.tiltDegrees) {
        fallRearmRequired = false;
        Serial.println("FALL_REARMED: head returned upright.");
    }
    refreshAlarm(now);
    static uint32_t publishedAt = 0;
    if (now - publishedAt >= 100) {
        publishedAt = now;
        HelmetSnapshot state;
        state.gasReady = HELMET_ENABLE_GAS && gasSensor.isWarmedUp();
        if (HELMET_ENABLE_GAS) {
            state.gasPpm = gasSensor.getPpm(); state.gasAdc = gasSensor.getFilteredADC();
            state.gasVoltage = gasSensor.getVoltage();
            state.gasWarmupSeconds = gasSensor.getRemainingWarmupTime();
        }
        state.accelerationG = lastAccelerationG;
        state.tiltDegrees = tilt;
        state.sensorOk = !sensorFault && isfinite(lastAccelerationG);
        state.calibrated = hasPosture;
        state.wornKnown = HELMET_ENABLE_WEAR;
        state.worn = worn;
        state.fall = fallDetector.state() == helmet::FallState::FALL_ALERT;
        state.buzzerOn = alarmPolicy.soundState(fallDetector.state() == helmet::FallState::PENDING_CONFIRMATION)
                         != helmet::FallState::MONITORING;
        strlcpy(state.fallState, helmet::fallStateName(fallDetector.state()), sizeof(state.fallState));
        updateHelmetSnapshot(state);
    }
    static uint32_t statusAt = 0;
    if (now - statusAt >= 2000) {
        statusAt = now;
        Serial.printf("STATUS: worn=%s gas=%d fall=%s button=%s buzzer=%d\n",
                      HELMET_ENABLE_WEAR ? (worn ? "YES" : "NO") : "UNKNOWN", gasState,
                      helmet::fallStateName(fallDetector.state()),
                      digitalRead(CONFIRM_BUTTON_PIN) == LOW ? "LOW" : "HIGH", buzzerActive);
        if (HELMET_ENABLE_GAS) Serial.printf("GAS: ppm=%.1f adc=%.0f voltage=%.3f V warmup_remaining=%lu s\n",
            gasSensor.getPpm(), gasSensor.getFilteredADC(), gasSensor.getVoltage(),
            static_cast<unsigned long>(gasSensor.getRemainingWarmupTime()));
    }
}

// So sánh tuyệt đối là cố ý: cùng một mẫu raw sẽ cho ra cùng một giá trị float.
bool sameSample(const Reading& a, const Reading& b) {
    return a.ax == b.ax && a.ay == b.ay && a.az == b.az &&
           a.gx == b.gx && a.gy == b.gy && a.gz == b.gz &&
           a.temperature == b.temperature;
}

void setup() {
    digitalWrite(BUZZER_CONTROL_PIN, LOW);
    pinMode(BUZZER_CONTROL_PIN, OUTPUT);
    buzzerReady = true;
    if (BUZZER_IS_PASSIVE) {
        buzzerReady = ledcSetup(BUZZER_PWM_CHANNEL, BUZZER_TONE_HZ, BUZZER_PWM_BITS) != 0;
        if (buzzerReady) ledcAttachPin(BUZZER_CONTROL_PIN, BUZZER_PWM_CHANNEL);
    }
    fallAlarm.begin();
    pinMode(CONFIRM_BUTTON_PIN, INPUT_PULLUP);
    confirmButton.begin(digitalRead(CONFIRM_BUTTON_PIN) == LOW, millis());
    Serial.begin(115200);
    delay(300);
    Serial.println("SMART_HELMET INTEGRATED_V2_AUTOCONNECT: c=calibrate, p=reference, x=cancel, k/r=mute, b=beep, s=SOS.");
    Serial.printf("FALL_CONFIG: tilt>%.1f deg; D27=mute, buzzer=D13.\n", helmet::FallConfig{}.tiltDegrees);
    if (!buzzerReady) Serial.println("BUZZER_FAULT: PWM initialization failed.");
    else if (BUZZER_IS_PASSIVE) {
        Serial.printf("BUZZER_READY: passive PWM %lu Hz on GPIO%d.\n",
                      static_cast<unsigned long>(BUZZER_TONE_HZ), BUZZER_CONTROL_PIN);
    } else Serial.printf("BUZZER_READY: active-high driver on GPIO%d.\n", BUZZER_CONTROL_PIN);
    Serial.printf("BUTTON_READY: GPIO%d to GND, INPUT_PULLUP, press to acknowledge and stop buzzer (30 ms debounce).\n",
                  CONFIRM_BUTTON_PIN);
    Serial.printf("BUTTON_LEVEL: GPIO%d=%s (released=HIGH, pressed=LOW).\n",
                  CONFIRM_BUTTON_PIN, digitalRead(CONFIRM_BUTTON_PIN) == LOW ? "LOW" : "HIGH");
    if (HELMET_ENABLE_WEAR) {
        pinMode(PIN_TOUCH_WORN, INPUT_PULLUP);
        worn = digitalRead(PIN_TOUCH_WORN) == LOW;
    }
    if (HELMET_ENABLE_SOS) {
        pinMode(PIN_SOS_BUTTON, INPUT_PULLUP);
        sosButton.begin(digitalRead(PIN_SOS_BUTTON) == LOW, millis());
    }
    if (HELMET_ENABLE_GAS) gasSensor.begin();
    beginHelmetServices();
    loadPosture();

    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN, 100000);
    Wire.setTimeOut(20);

    Serial.println("\n=== MPU6050 connection test ===");
    Serial.printf("I2C: SDA=GPIO%d, SCL=GPIO%d, 100 kHz\n",
                  I2C_SDA_PIN, I2C_SCL_PIN);

    scanBus();

    sensorAddress = findSensor();
    if (sensorAddress == 0) {
        Serial.println("RESULT: FAIL - no device at 0x68 or 0x69.");
        return;
    }

    uint8_t whoAmI = 0;
    if (!readRegister(sensorAddress, REG_WHO_AM_I, whoAmI)) {
        Serial.println("RESULT: FAIL - cannot read WHO_AM_I.");
        sensorAddress = 0;
        return;
    }

    Serial.printf("WHO_AM_I @0x%02X = 0x%02X\n",
                  sensorAddress, whoAmI);

    if (whoAmI != WHO_AM_I_MPU6050) {
        Serial.println("RESULT: FAIL - device is not MPU6050.");
        sensorAddress = 0;
        return;
    }

    if (!configureSensor()) {
        Serial.println("RESULT: FAIL - register configuration failed.");
        sensorAddress = 0;
        return;
    }

    delay(100);

    Reading firstReading{};
    if (!readReading(firstReading)) {
        Serial.println("RESULT: FAIL - cannot read first sample.");
        sensorAddress = 0;
        return;
    }

    float gravity = magnitude(firstReading.ax,
                              firstReading.ay,
                              firstReading.az);

    Serial.printf("First sample: |a|=%.2f g, temperature=%.2f C\n",
                  gravity, firstReading.temperature);

    if (gravity < 0.5f || gravity > 1.5f) {
        Serial.println("Startup motion detected; posture is NOT auto-calibrated.");
    }

    Serial.println("RESULT: PASS - MPU6050 is responding.");
    Serial.println("Tilt / shake the board: values should change.\n");
}

void loop() {
    // Deadlines continue to run even when sensor paths below return early.
    const uint32_t inputTime = millis();
    fallDetector.tick(inputTime);
    handleConfirmButton(inputTime);
    handleCommands();
    updateIntegratedServices(millis());
    static uint32_t lastSample = 0;
    static uint32_t lastPrint = 0;
    static Reading previous{};
    static bool hasPrevious = false;
    static uint16_t stallCount = 0;
    static uint16_t samplesSincePrint = 0;
    static uint8_t consecutiveReadFailures = 0;

    if (millis() - lastSample < SAMPLE_INTERVAL_MS) {
        return;
    }
    lastSample = millis();

    if (sensorAddress == 0) {
        if (!sensorFault) {
            sensorFault = true;
            Serial.println("SENSOR_FAULT: MPU6050 unavailable, retrying.");
        }
        consecutiveReadFailures = 0;
        hasPrevious = false;
        stallCount = 0;
        samplesSincePrint = 0;
        ensureSensor(true);
        return;
    }

    // A transient bus error cancels calibration, but should not cause
    // an immediate reconfiguration. Recover after three failed sample cycles.
    auto handleReadFailure = [&](const char* operation) {
        hasPrevious = false;
        stallCount = 0;
        samplesSincePrint = 0;
        resetFallDetector("missing sensor sample");
        ++consecutiveReadFailures;
        Serial.printf("MPU6050 read failure %u/%u: %s, I2C=%u, bytes=%u\n",
                      consecutiveReadFailures, MAX_CONSECUTIVE_READ_FAILURES,
                      operation, lastI2cError, lastReadBytes);
        if (consecutiveReadFailures >= MAX_CONSECUTIVE_READ_FAILURES) {
            if (!sensorFault) {
                sensorFault = true;
                Serial.println("SENSOR_FAULT: repeated MPU6050 read failures.");
            }
            consecutiveReadFailures = 0;
            sensorAddress = 0;
            ensureSensor(true);
        }
    };

    uint8_t power = 0;
    if (!readRegister(sensorAddress, REG_PWR_MGMT_1, power)) {
        handleReadFailure("power register");
        return;
    }

    if ((power & PWR_MGMT_1_SLEEP) != 0) {
        consecutiveReadFailures = 0;
        hasPrevious = false;
        stallCount = 0;
        samplesSincePrint = 0;
        Serial.printf("MPU6050 went to sleep (0x%02X).\n", power);
        ensureSensor(true);
        return;
    }

    Reading reading{};
    if (!readReading(reading)) {
        handleReadFailure("sample");
        return;
    }
    lastAccelerationG = magnitude(reading.ax, reading.ay, reading.az);
    consecutiveReadFailures = 0;
    if (sensorFault) {
        sensorFault = false;
        Serial.println("SENSOR_RECOVERED: valid MPU6050 samples resumed.");
    }

    if (hasPrevious && sameSample(reading, previous)) {
        ++stallCount;
        if (stallCount >= STALL_SAMPLES) {
            stallCount = 0;
            hasPrevious = false;
            samplesSincePrint = 0;
            Serial.println("Data frozen - re-initialising MPU6050.");
            ensureSensor(true);
            return;
        }
    } else {
        stallCount = 0;
    }

    previous = reading;
    hasPrevious = true;
    ++samplesSincePrint;

    const bool wasCalibrating = calibration.countdown || calibration.collecting;
    updateCalibration(reading, millis());
    if (!wasCalibrating) {
        fallDetector.sample({reading.ax, reading.ay, reading.az},
                            {reading.gx, reading.gy, reading.gz}, millis(),
                            (!HELMET_ENABLE_WEAR || worn) && !fallRearmRequired);
    }

    // Vẫn đọc mỗi 20 ms, nhưng chỉ in một dòng mỗi 500 ms.
    if (millis() - lastPrint < PRINT_INTERVAL_MS) {
        return;
    }
    lastPrint = millis();

    float gravity = magnitude(reading.ax, reading.ay, reading.az);

    Serial.printf(
        "samples/500ms=%u | a[g] %6.2f %6.2f %6.2f "
        "|a|=%5.2f | gyro_raw[dps] %7.2f %7.2f %7.2f | %.1f C",
        samplesSincePrint,
        reading.ax, reading.ay, reading.az, gravity,
        reading.gx, reading.gy, reading.gz,
        reading.temperature
    );

    printPostureAngle();
    samplesSincePrint = 0;
}
