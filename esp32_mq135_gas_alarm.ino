/**
 * ============================================================================
 * DỰ ÁN: HỆ THỐNG CẢNH BÁO KHÍ GAS & CHẤT LƯỢNG KHÔNG KHÍ (ESP32 + MQ-135)
 * ============================================================================
 * Phần cứng:
 *   - ESP32 DevKit V1 (30 chân hoặc 36 chân)
 *   - Cảm biến khí độc / gas MQ-135
 *   - Active Buzzer (Còi chíp 5V hoặc 3.3V)
 *   - LED Xanh lá (Safe), Vàng (Warning), Đỏ (Danger)
 * ============================================================================
 */

#include <Arduino.h>
#include "config.h"
#include "mq135_sensor.h"
#include "alarm_controller.h"

// Khởi tạo các đối tượng điều khiển
MQ135Sensor mq135(PIN_MQ135_ANALOG, PIN_MQ135_DIGITAL);
AlarmController alarm(PIN_BUZZER, PIN_LED_GREEN, PIN_LED_YELLOW, PIN_LED_RED);

// Biến lưu trạng thái hệ thống
SystemState currentState = STATE_WARMING_UP;
unsigned long lastSerialPrint = 0;
const unsigned long SERIAL_PRINT_INTERVAL = 1000; // In log mỗi 1 giây

// Hàm vẽ thanh bar trực quan trên Serial Monitor
void printVisualBar(float adcValue, float maxADC = 4095.0f, uint8_t barWidth = 30) {
    uint8_t filled = (uint8_t)((adcValue / maxADC) * barWidth);
    if (filled > barWidth) filled = barWidth;

    Serial.print("[");
    for (uint8_t i = 0; i < barWidth; i++) {
        if (i < filled) {
            Serial.print(i > (barWidth * 0.6) ? "!" : "#");
        } else {
            Serial.print("-");
        }
    }
    Serial.print("]");
}

// Chuyển đổi trạng thái sang chuỗi text
const char* getStateString(SystemState state) {
    switch (state) {
        case STATE_WARMING_UP: return "ĐANG KHỞI ĐỘNG (SẤY SENSOR)";
        case STATE_SAFE:       return "AN TOÀN (SAFE)";
        case STATE_WARNING:    return "CẢNH BÁO (WARNING)";
        case STATE_DANGER:     return "NGUY HIỂM (DANGER)";
        default:               return "UNKNOWN";
    }
}

// Hàm móc nối (Hook) để gửi cảnh báo qua Wi-Fi / Telegram / Blynk trong tương lai
void handleAlertNotification(SystemState oldState, SystemState newState) {
    if (newState == STATE_DANGER && oldState != STATE_DANGER) {
        Serial.println(F(">>> [ALARM HOOK] KÍCH HOẠT CẢNH BÁO KHẨN CẤP! GỬI TELEGRAM / BLYNK / MQTT <<<"));
        // TODO: Gọi hàm gửi tin nhắn qua Telegram Bot hoặc MQTT tại đây
    } else if (newState == STATE_SAFE && oldState != STATE_SAFE) {
        Serial.println(F(">>> [ALARM HOOK] Hệ thống đã trở về trạng thái AN TOÀN. <<<"));
    }
}

void setup() {
    Serial.begin(115200);
    delay(1000);

    Serial.println();
    Serial.println(F("=================================================="));
    Serial.println(F("   HỆ THỐNG CẢNH BÁO KHÍ GAS & KHÔNG KHÍ ESP32    "));
    Serial.println(F("              Cảm biến: MQ-135                    "));
    Serial.println(F("=================================================="));

    // Khởi tạo phần cứng
    mq135.begin();
    alarm.begin();

    // Kêu 2 tiếng bíp ngắn báo hiệu khởi động thành công
    alarm.triggerBeep(80);
    delay(150);
    alarm.triggerBeep(80);

    Serial.println(F("[INFO] Bắt đầu quá trình sấy cảm biến (Warm-up)..."));
}

void loop() {
    // 1. Cập nhật các tác vụ phần cứng
    mq135.update();
    alarm.update();

    // 2. Máy trạng thái (Finite State Machine) xác định mức độ nguy hiểm
    SystemState previousState = currentState;

    if (!mq135.isWarmedUp()) {
        currentState = STATE_WARMING_UP;
    } else {
        float currentADC = mq135.getFilteredADC();
        bool digitalAlarm = mq135.isDigitalTriggered();

        // Kiểm tra ngưỡng với độ trễ (Hysteresis) để tránh dao động
        if (currentADC >= ADC_THRESHOLD_DANGER || digitalAlarm) {
            currentState = STATE_DANGER;
        } else if (currentADC >= ADC_THRESHOLD_WARNING) {
            currentState = STATE_WARNING;
        } else if (currentADC <= (ADC_THRESHOLD_WARNING - ADC_HYSTERESIS)) {
            currentState = STATE_SAFE;
        }
    }

    // 3. Nếu trạng thái thay đổi -> cập nhật bộ điều khiển cảnh báo
    if (currentState != previousState) {
        alarm.setState(currentState);
        handleAlertNotification(previousState, currentState);
    }

    // 4. In thông tin giám sát định kỳ ra Serial Monitor
    unsigned long now = millis();
    if (now - lastSerialPrint >= SERIAL_PRINT_INTERVAL) {
        lastSerialPrint = now;

        float filteredADC = mq135.getFilteredADC();
        float voltage = mq135.getVoltage();
        int rawADC = mq135.getRawADC();

        Serial.print(F("["));
        Serial.print(now / 1000);
        Serial.print(F("s] State: "));
        Serial.print(getStateString(currentState));

        if (!mq135.isWarmedUp()) {
            Serial.print(F(" | Còn sấy: "));
            Serial.print(mq135.getRemainingWarmupTime());
            Serial.println(F(" giây..."));
        } else {
            Serial.print(F(" | ADC Raw: "));
            Serial.print(rawADC);
            Serial.print(F(" | ADC Lọc: "));
            Serial.print((int)filteredADC);
            Serial.print(F(" | Vout: "));
            Serial.print(voltage, 2);
            Serial.print(F("V | D0: "));
            Serial.print(mq135.isDigitalTriggered() ? "TRIG" : "IDLE");
            Serial.print(F(" "));
            printVisualBar(filteredADC);
            Serial.println();
        }
    }
}
