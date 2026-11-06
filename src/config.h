/**
 * @file config.h
 * @brief Cấu hình chân kết nối, ngưỡng cảnh báo và tham số hệ thống
 * Tuân thủ sơ đồ kết nối chân và thông số trong smart-helmet-spec.md.
 */

#ifndef CONFIG_H
#define CONFIG_H
#define SIMULATE_SENSOR 1

#include <Arduino.h>

// ==========================================
// 1. CẤU HÌNH CHÂN (PINOUT) - ESP32 DEVKIT V1 (Bảng 3.4)
// ==========================================
// Cảm biến khí MQ-135
#define PIN_MQ135_ANALOG    34      // ADC1_CH6 (tín hiệu AO qua cầu phân áp an toàn 3.3V)
#define PIN_MQ135_DIGITAL   35      // Tín hiệu DO từ LM393

// Cảm biến chuyển động MPU6050 (6 trục)
#define PIN_I2C_SDA         21      // SDA MPU6050
#define PIN_I2C_SCL         22      // SCL MPU6050
#define MPU6050_I2C_ADDR    0x68

// Module GPS NEO-6M nối với UART2 của ESP32
#define PIN_GPS_RX          16      // RX2 nối TX của NEO-6M
#define PIN_GPS_TX          17      // TX2 nối RX của NEO-6M
#define GPS_BAUD_RATE       9600

// Cảm ứng điện dung nhận biết đội mũ (Touch Pin)
#define PIN_TOUCH_WORN      4       // Touch0 (T0 - GPIO4) nối tấm dẫn điện trong vành mũ
#define TOUCH_THRESHOLD     40      // < 40 là đang đội mũ, > 50 là tháo mũ

// Nút nhấn khẩn cấp SOS và xác nhận an toàn
#define PIN_SOS_BUTTON      27      // GPIO27 nối nút nhấn vật lý, đầu kia nối GND (INPUT_PULLUP)

// Thiết bị phát âm thanh cảnh báo (Active Buzzer)
#define PIN_BUZZER          25      // GPIO25 điều khiển còi báo động

// ==========================================
// 2. KẾT NỐI MẠNG & MQTT (Mục 3.4)
// ==========================================
#define WIFI_SSID           "YOUR_WIFI_SSID"
#define WIFI_PASSWORD       "YOUR_WIFI_PASSWORD"
#define MQTT_BROKER         "192.168.1.100"
#define MQTT_PORT           1883
#define MQTT_USERNAME       ""
#define MQTT_PASSWORD       ""
#define DEVICE_UID          "HELMET_001"
#define DEVICE_ID           "HELMET_001"

// Cấu trúc topic MQTT (Bảng 3.5)
#define TOPIC_TELEMETRY     "helmet/device/" DEVICE_UID "/telemetry"
#define TOPIC_ALERT         "helmet/device/" DEVICE_UID "/alert"
#define TOPIC_STATUS        "helmet/device/" DEVICE_UID "/status"
#define TOPIC_STATE         "helmet/device/" DEVICE_UID "/state"
#define TOPIC_COMMAND       "helmet/device/" DEVICE_UID "/command"

#define MQTT_RECONNECT_INTERVAL_MS 5000UL
#define TELEMETRY_INTERVAL_MS      5000UL  // Định kỳ 5 giây theo yêu cầu F01 Bảng 3.1
#define EVENT_QUEUE_CAPACITY       8

// ==========================================
// 3. CẤU HÌNH THỜI GIAN & LỌC TÍN HIỆU
// ==========================================
#define SENSOR_SAMPLE_INTERVAL_MS   100     // Chu kỳ đọc cảm biến (ms)
#define SENSOR_WARMUP_SECONDS       30      // Thời gian sấy cảm biến lúc khởi động
#define FILTER_ALPHA                0.15f   // Hệ số lọc làm mượt EMA (Exponential Moving Average)

// ==========================================
// 4. CẤU HÌNH NGƯỠNG CẢNH BÁO (Bảng 3.11)
// ==========================================
#define GAS_WARN_PPM_DEFAULT        300.0f  // Ngưỡng khí cảnh báo (ppm)
#define GAS_DANGER_PPM_DEFAULT      600.0f  // Ngưỡng khí nguy hiểm (ppm)
#define GAS_HYSTERESIS_PPM          25.0f   // Khoảng đệm trễ hạ mức an toàn

// Ngưỡng phát hiện té ngã (Không TinyML - dùng ngưỡng gia tốc đỉnh kết hợp bất động)
#define FALL_ACC_THRESHOLD_DEFAULT  2.5f    // Ngưỡng gia tốc va chạm đỉnh (g)
#define FALL_INACTIVITY_SEC_DEFAULT 15      // Thời gian bất động xác nhận ngã (giây)

// Ngưỡng ADC cũ để tương thích
#define ADC_THRESHOLD_WARNING       1200
#define ADC_THRESHOLD_DANGER        2200
#define ADC_HYSTERESIS              50

// ==========================================
// 5. ĐỊNH NGHĨA TRẠNG THÁI HỆ THỐNG
// ==========================================
enum SystemState {
    STATE_WARMING_UP,   // Đang sấy cảm biến
    STATE_SAFE,         // Bình thường, an toàn
    STATE_WARNING,      // Khí vượt ngưỡng chú ý (WARNING)
    STATE_DANGER,       // Khí nguy hiểm / Té ngã / SOS (DANGER)
    STATE_FALL          // Xác nhận té ngã
};

#endif // CONFIG_H
