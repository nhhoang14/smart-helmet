/**
 * @file config.h
 * @brief Cấu hình chân kết nối, ngưỡng cảnh báo và tham số hệ thống
 */

#ifndef CONFIG_H
#define CONFIG_H

#include <Arduino.h>

// ==========================================
// 1. CẤU HÌNH CHÂN (PINOUT) - ESP32 DEVKIT V1
// ==========================================
// Chú ý: Dùng chân thuộc ADC1 (GPIO 32 - 39) để không bị xung đột khi bật Wi-Fi!
#define PIN_MQ135_ANALOG    34      // ADC1_CH6 (Input only - rất thích hợp cho cảm biến)
#define PIN_MQ135_DIGITAL   35      // Tín hiệu DO từ MQ-135 (nếu sử dụng ngưỡng so sánh biến trở trên module)

// Chân điều khiển cảnh báo
#define PIN_BUZZER          25      // Còi báo động (Active Buzzer hoặc Passive Buzzer)
#define PIN_LED_GREEN       18      // Đèn xanh: Trạng thái an toàn
#define PIN_LED_YELLOW      19      // Đèn vàng: Trạng thái cảnh báo nhẹ
#define PIN_LED_RED         21      // Đèn đỏ: Trạng thái nguy hiểm

// ==========================================
// 2. CẤU HÌNH THỜI GIAN & LỌC TÍN HIỆU
// ==========================================
#define SENSOR_SAMPLE_INTERVAL_MS   200     // Chu kỳ đọc cảm biến (ms)
#define SENSOR_WARMUP_SECONDS       30      // Thời gian sấy cảm biến lúc khởi động (khuyên dùng 30-60s khi test, thực tế cần sấy lâu hơn)
#define FILTER_ALPHA                0.15f   // Hệ số lọc làm mượt EMA (Exponential Moving Average, 0.05 - 0.3)

// ==========================================
// 3. CẤU HÌNH NGƯỠNG CẢNH BÁO (ADC 12-bit: 0 - 4095)
// ==========================================
// Giá trị ADC thực tế phụ thuộc điện áp môi trường và hiệu chỉnh biến trở trên module.
// Bạn có thể tinh chỉnh các giá trị này qua Serial Monitor.
#define ADC_THRESHOLD_WARNING       1200    // Vượt ngưỡng này -> Chuyển sang CẢNH BÁO (WARNING)
#define ADC_THRESHOLD_DANGER        2200    // Vượt ngưỡng này -> Chuyển sang NGUY HIỂM (DANGER)
#define ADC_HYSTERESIS              50      // Độ trễ chống chập chờn trạng thái

// ==========================================
// 4. ĐỊNH NGHĨA TRẠNG THÁI HỆ THỐNG
// ==========================================
enum SystemState {
    STATE_WARMING_UP,   // Đang sấy cảm biến
    STATE_SAFE,         // Bình thường, không khí an toàn
    STATE_WARNING,      // Phát hiện nồng độ khí tăng nhẹ
    STATE_DANGER        // Nồng độ khí vượt ngưỡng nguy hiểm
};

#endif // CONFIG_H
