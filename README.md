# Hệ Thống Cảnh Báo Khí Gas & Chất Lượng Không Khí (ESP32 DevKit V1 + MQ-135)

Dự án mẫu chuẩn kiến trúc mô-đun (modular), non-blocking (không sử dụng `delay()` gây đơ chương trình), tích hợp bộ lọc chống nhiễu ADC và máy trạng thái đa tầng (State Machine).

---

## 1. Sơ Đồ Đấu Nối Phần Cứng (Wiring Diagram)

```
        +-----------------------------------------------+
        |                 ESP32 DevKit V1               |
        |                                               |
        |  [VIN / 5V] ----------------> VCC (MQ-135)    |
        |  [GND]      ----------------> GND (MQ-135)    |
        |  [GPIO 34]  <---------------- AOUT / AO       |
        |  [GPIO 35]  <---------------- DOUT / DO       |
        |                                               |
        |  [GPIO 25]  ----------------> (+) Còi Buzzer  |
        |  [GND]      ----------------> (-) Còi Buzzer  |
        |                                               |
        |  [GPIO 18]  ----> [R 220Ω] -> (+) LED Xanh    |
        |  [GPIO 19]  ----> [R 220Ω] -> (+) LED Vàng    |
        |  [GPIO 21]  ----> [R 220Ω] -> (+) LED Đỏ      |
        |  [GND]      ----------------> (-) Các LED     |
        +-----------------------------------------------+
```

### Bảng Kết Nối Chi Tiết:

| Thiết bị | Chân thiết bị | Chân ESP32 DevKit V1 | Ghi chú quan trọng |
| :--- | :--- | :--- | :--- |
| **MQ-135** | **VCC** | **VIN (hoặc 5V)** | *Bắt buộc cấp 5V* để cuộn sấy bên trong MQ-135 hoạt động sinh nhiệt phản ứng hóa học. |
| **MQ-135** | **GND** | **GND** | Nối chung mass. |
| **MQ-135** | **AO (Analog Out)** | **GPIO 34** | Thuộc **ADC1**. Tuyệt đối không dùng các chân ADC2 nếu có bật Wi-Fi. *(Khuyên dùng cầu phân áp 10k/20k nếu nồng độ khí cực cao để bảo vệ ADC 3.3V)*. |
| **MQ-135** | **DO (Digital Out)**| **GPIO 35** | Nhận tín hiệu so sánh ngưỡng từ IC LM393 trên module (có thể dùng hoặc bỏ trống). |
| **Active Buzzer** | Chân dương (+) | **GPIO 25** | Còi báo động dạng Active (chỉ cần cấp mức HIGH là kêu). |
| **Active Buzzer** | Chân âm (-) | **GND** | Nối mass. |
| **LED Xanh lá** | Anode (+) | **GPIO 18** | Qua điện trở 220Ω - Báo trạng thái **An toàn**. |
| **LED Vàng** | Anode (+) | **GPIO 19** | Qua điện trở 220Ω - Báo trạng thái **Cảnh báo**. |
| **LED Đỏ** | Anode (+) | **GPIO 21** | Qua điện trở 220Ω - Báo trạng thái **Nguy hiểm**. |

---

## 2. Điểm Đặc Biệt Trong Thiết Kế Code

1. **Bộ lọc làm mượt tín hiệu (Oversampling & Exponential Moving Average - EMA):**
   - Bộ ADC của ESP32 thường có độ nhiễu điện từ nhất định.
   - Code lấy trung bình 8-16 mẫu vi mô kết hợp lọc làm mượt số thực giúp giá trị khí đo được cực kỳ ổn định, không bị chập chờn báo ảo.
2. **Cơ chế chống rung trạng thái (Hysteresis):**
   - Tránh hiện tượng còi/LED bị bật/tắt liên tục khi nồng độ khí dao động mấp mé ngay ngưỡng báo động.
3. **Non-blocking 100%:**
   - Dùng `millis()` thay vì `delay()`. Còi có thể phát chu kỳ bíp (ví dụ: bíp ngắt quãng ở mức Warning, hụ dồn dập ở mức Danger) trong khi cảm biến vẫn liên tục đọc mẫu thời gian thực.
4. **Giai đoạn sấy cảm biến (Warm-up):**
   - Cảm biến MQ-135 sau khi cấp nguồn cần sấy nóng màng oxit kim loại SnO2. Khi mới bật, giá trị đọc thường rất cao và ảo. Code tự động kích hoạt chế độ Warm-up (30-60s) để ngăn báo động giả khi vừa cắm điện.

---

## 3. Các Trạng Thái Hệ Thống

| Trạng thái | Điều kiện kích hoạt | Đèn LED | Còi Buzzer |
| :--- | :--- | :--- | :--- |
| **WARMING_UP** | 30 giây đầu sau khi bật nguồn | LED Vàng & Xanh chớp luân phiên | Tắt hoàn toàn |
| **SAFE** | `ADC < 1200` | LED Xanh sáng | Tắt |
| **WARNING** | `1200 <= ADC < 2200` | LED Vàng sáng | Kêu ngắt quãng (150ms mỗi 1.5s) |
| **DANGER** | `ADC >= 2200` hoặc chân `DO` kích hoạt | LED Đỏ chớp dồn dập (120ms) | Hụ dồn dập (120ms ON / 120ms OFF) |

---

## 4. Hướng Dẫn Cân Chỉnh (Calibration) Thực Tế

1. **Bước 1 - Sấy cảm biến (Burn-in ban đầu):**
   - Khi mới mua MQ-135 về, hãy cắm nguồn 5V cho cảm biến chạy liên tục **24 - 48 giờ** trong phòng thông thoáng để lớp màng hóa học ổn định hoàn toàn. Cảm biến hơi ấm ở phần vỏ kim loại là hiện tượng bình thường.
2. **Bước 2 - Lấy mẫu không khí sạch:**
   - Bật Serial Monitor (Baudrate: **115200**).
   - Quan sát giá trị `ADC Lọc` trong điều kiện không khí bình thường (ví dụ: dao động trong khoảng 400 - 800).
3. **Bước 3 - Thử nghiệm với khí mẫu:**
   - Dùng bật lửa (ấn xả ga nhẹ gần cảm biến, **không đánh lửa**) hoặc cồn y tế để quan sát giá trị ADC nhảy vọt lên 2500 - 3500.
4. **Bước 4 - Tinh chỉnh ngưỡng trong file `config.h`:**
   - Thay đổi `#define ADC_THRESHOLD_WARNING` và `#define ADC_THRESHOLD_DANGER` cho phù hợp với môi trường thực tế của bạn.

---

## 5. Hướng Dẫn Nạp Code

### Cách 1: Nạp bằng Arduino IDE
1. Cài đặt board ESP32 trong Arduino IDE (`Tools` -> `Board` -> `esp32` -> `DOIT ESP32 DEVKIT V1`).
2. Mở file [esp32_mq135_gas_alarm.ino](file:///Users/mac/.gemini/antigravity-ide/scratch/esp32_mq135_gas_alarm/esp32_mq135_gas_alarm.ino).
3. Nhấn nút **Upload** và mở **Serial Monitor** ở tốc độ **115200 bps**.

### Cách 2: Nạp bằng PlatformIO
```bash
pio run -t upload
pio device monitor -b 115200
```
