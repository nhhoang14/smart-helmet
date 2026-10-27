#include <Arduino.h>
#include <Wire.h>
#include <math.h>

constexpr int I2C_SDA_PIN = 21;
constexpr int I2C_SCL_PIN = 22;
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
constexpr float ACCEL_LSB_PER_G = 16384.0f;  // Thang đo ±2 g
constexpr float GYRO_LSB_PER_DPS = 131.0f;   // Thang đo ±250 độ/giây
constexpr float SHAKE_THRESHOLD_G = 0.30f;

const uint8_t CANDIDATE_ADDRESSES[] = {0x68, 0x69};
uint8_t sensorAddress = 0;

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
    Wire.beginTransmission(address);
    Wire.write(reg);

    if (Wire.endTransmission(false) != 0) {
        return false;
    }

    if (Wire.requestFrom(address, length) != length) {
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
           writeRegister(sensorAddress, REG_SMPLRT_DIV, 0x00) &&
           writeRegister(sensorAddress, REG_CONFIG, 0x03) &&
           writeRegister(sensorAddress, REG_GYRO_CONFIG, 0x00) &&
           writeRegister(sensorAddress, REG_ACCEL_CONFIG, 0x00);
}

void restartBus() {
    Wire.end();
    pinMode(I2C_SDA_PIN, INPUT_PULLUP);
    pinMode(I2C_SCL_PIN, INPUT_PULLUP);
    delay(10);
    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN, 100000);
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

    // A recovery attempt invalidates both the event and its gravity history.
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

constexpr uint8_t FALL_REFERENCE_SAMPLES = 25;
constexpr uint32_t FALL_WINDOW_MS = 3000;
constexpr uint32_t FALL_STABLE_MS = 1000;
constexpr uint32_t FALL_COOLDOWN_MS = 3000;
constexpr float FALL_MIN_ANGLE_DEG = 50.0f;

struct GravityVector {
    float x, y, z;
};

struct FallDetector {
    GravityVector reference{};
    GravityVector beforeEvent{};
    uint8_t referenceSamples = 0;
    bool tracking = false;
    bool stable = false;
    bool hasAlert = false;
    uint32_t eventStarted = 0;
    uint32_t stableStarted = 0;
    uint32_t lastStableSample = 0;
    uint32_t lastAlert = 0;
};

FallDetector fallDetector;

void cancelFallEvent(const char* reason) {
    if (fallDetector.tracking) {
        Serial.printf("FALL cancel: %s\n", reason);
    }
    fallDetector.tracking = false;
    fallDetector.stable = false;
}

void resetFallDetector(const char* reason) {
    cancelFallEvent(reason);
    fallDetector.reference = {};
    fallDetector.referenceSamples = 0;
    // Keep lastAlert/hasAlert so recovery cannot bypass the cooldown.
}

float gravityAngle(const GravityVector& before, const Reading& after,
                   float acceleration) {
    const float norm = magnitude(before.x, before.y, before.z) * acceleration;
    if (norm <= 0.0f) {
        return 0.0f;
    }
    float cosine = (before.x * after.ax + before.y * after.ay +
                    before.z * after.az) / norm;
    cosine = fmaxf(-1.0f, fminf(1.0f, cosine));
    return acosf(cosine) * (180.0f / PI);
}

// Called only for accepted samples, at the existing 20 ms sampling cadence.
void updateFallDetector(const Reading& reading, uint32_t now) {
    const float acceleration = magnitude(reading.ax, reading.ay, reading.az);
    const float gyro = magnitude(reading.gx, reading.gy, reading.gz);
    const bool nearGravity = acceleration >= 0.8f && acceleration <= 1.2f;

    if (fallDetector.tracking) {
        if (now - fallDetector.eventStarted > FALL_WINDOW_MS) {
            cancelFallEvent("timeout");
        } else {
            const float angle = gravityAngle(fallDetector.beforeEvent, reading,
                                             acceleration);
            if (nearGravity && gyro < 10.0f && angle >= FALL_MIN_ANGLE_DEG) {
                // A missed sampling interval cannot count as observed stability.
                if (!fallDetector.stable ||
                    now - fallDetector.lastStableSample > 2 * SAMPLE_INTERVAL_MS) {
                    fallDetector.stable = true;
                    fallDetector.stableStarted = now;
                }
                fallDetector.lastStableSample = now;
                if (now - fallDetector.stableStarted >= FALL_STABLE_MS) {
                    Serial.printf("SUSPECTED_FALL angle=%.1f deg\n", angle);
                    fallDetector.lastAlert = now;
                    fallDetector.hasAlert = true;
                    fallDetector.tracking = false;
                    fallDetector.stable = false;
                    // Relearn the new posture before considering another event.
                    fallDetector.reference = {};
                    fallDetector.referenceSamples = 0;
                }
            } else {
                fallDetector.stable = false;
            }
            return;
        }
    }

    const bool cooldown = fallDetector.hasAlert &&
                         now - fallDetector.lastAlert < FALL_COOLDOWN_MS;
    if (!cooldown && fallDetector.referenceSamples >= FALL_REFERENCE_SAMPLES &&
        (acceleration >= 1.7f || gyro >= 120.0f)) {
        fallDetector.beforeEvent = fallDetector.reference;
        fallDetector.eventStarted = now;
        fallDetector.tracking = true;
        fallDetector.stable = false;
        Serial.printf("FALL start: |a|=%.2f g gyro=%.1f dps\n", acceleration, gyro);
        return;
    }

    if (nearGravity && gyro < 15.0f) {
        // Average the first 25 eligible samples, then use a gentle moving average.
        const float weight = fallDetector.referenceSamples < FALL_REFERENCE_SAMPLES
                           ? 1.0f / (fallDetector.referenceSamples + 1) : 0.04f;
        fallDetector.reference.x += weight * (reading.ax - fallDetector.reference.x);
        fallDetector.reference.y += weight * (reading.ay - fallDetector.reference.y);
        fallDetector.reference.z += weight * (reading.az - fallDetector.reference.z);
        if (fallDetector.referenceSamples < FALL_REFERENCE_SAMPLES) {
            ++fallDetector.referenceSamples;
        }
    }
}

// So sánh tuyệt đối là cố ý: cùng một mẫu raw sẽ cho ra cùng một giá trị float.
bool sameSample(const Reading& a, const Reading& b) {
    return a.ax == b.ax && a.ay == b.ay && a.az == b.az &&
           a.gx == b.gx && a.gy == b.gy && a.gz == b.gz &&
           a.temperature == b.temperature;
}

void setup() {
    Serial.begin(115200);
    delay(300);

    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN, 100000);

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
        Serial.println("RESULT: FAIL - first sample looks invalid.");
        sensorAddress = 0;
        return;
    }

    Serial.println("RESULT: PASS - MPU6050 is responding.");
    Serial.println("Tilt / shake the board: values should change.\n");
}

void loop() {
    static uint32_t lastSample = 0;
    static uint32_t lastPrint = 0;
    static Reading previous{};
    static bool hasPrevious = false;
    static uint16_t stallCount = 0;
    static uint16_t samplesSincePrint = 0;

    if (millis() - lastSample < SAMPLE_INTERVAL_MS) {
        return;
    }
    lastSample = millis();

    if (sensorAddress == 0) {
        hasPrevious = false;
        stallCount = 0;
        samplesSincePrint = 0;
        ensureSensor(true);
        return;
    }

    uint8_t power = 0;
    if (!readRegister(sensorAddress, REG_PWR_MGMT_1, power)) {
        hasPrevious = false;
        stallCount = 0;
        samplesSincePrint = 0;
        Serial.println("Lost contact with MPU6050.");
        ensureSensor(true);
        return;
    }

    if ((power & PWR_MGMT_1_SLEEP) != 0) {
        hasPrevious = false;
        stallCount = 0;
        samplesSincePrint = 0;
        Serial.printf("MPU6050 went to sleep (0x%02X).\n", power);
        ensureSensor(true);
        return;
    }

    Reading reading{};
    if (!readReading(reading)) {
        hasPrevious = false;
        stallCount = 0;
        samplesSincePrint = 0;
        Serial.println("Sample rejected.");
        ensureSensor(true);
        return;
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

    updateFallDetector(reading, millis());

    // Vẫn đọc mỗi 20 ms, nhưng chỉ in một dòng mỗi 500 ms.
    if (millis() - lastPrint < PRINT_INTERVAL_MS) {
        return;
    }
    lastPrint = millis();

    float gravity = magnitude(reading.ax, reading.ay, reading.az);

    Serial.printf(
        "samples/500ms=%u | a[g] %6.2f %6.2f %6.2f "
        "|a|=%5.2f | g[dps] %7.2f %7.2f %7.2f | %.1f C\n",
        samplesSincePrint,
        reading.ax, reading.ay, reading.az, gravity,
        reading.gx, reading.gy, reading.gz,
        reading.temperature
    );

    samplesSincePrint = 0;
}
