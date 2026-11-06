# Hệ Thống Cảnh Báo Khí Gas & Định Vị GPS (ESP32 DevKit V1 + MQ-135)

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
        |  [GPIO 16]  <---------------- TX (NEO-6M)    |
        |  [GPIO 17]  ----------------> RX (NEO-6M)    |
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
| **NEO-6M GPS** | TX | **GPIO 16 (RX2)** | UART2, tốc độ 9600 baud. |
| **NEO-6M GPS** | RX | **GPIO 17 (TX2)** | Có thể bỏ trống nếu chỉ nhận tọa độ. |

---

## 2. Điểm Đặc Biệt Trong Thiết Kế Code

1. **Bộ lọc làm mượt tín hiệu (Oversampling & Exponential Moving Average - EMA):**
   - Bộ ADC của ESP32 thường có độ nhiễu điện từ nhất định.
   - Code lấy trung bình 8-16 mẫu vi mô kết hợp lọc làm mượt số thực giúp giá trị khí đo được cực kỳ ổn định, không bị chập chờn báo ảo.
2. **Cơ chế chống rung trạng thái (Hysteresis):**
   - Tránh hiện tượng còi bị bật/tắt liên tục khi nồng độ khí dao động mấp mé ngay ngưỡng báo động.
3. **Non-blocking 100%:**
   - Dùng `millis()` thay vì `delay()`. Còi có thể phát chu kỳ bíp (ví dụ: bíp ngắt quãng ở mức Warning, hụ dồn dập ở mức Danger) trong khi cảm biến vẫn liên tục đọc mẫu thời gian thực.
4. **Giai đoạn sấy cảm biến (Warm-up):**
   - Cảm biến MQ-135 sau khi cấp nguồn cần sấy nóng màng oxit kim loại SnO2. Khi mới bật, giá trị đọc thường rất cao và ảo. Code tự động kích hoạt chế độ Warm-up (30-60s) để ngăn báo động giả khi vừa cắm điện.

---

## 3. Các Trạng Thái Hệ Thống

| Trạng thái | Điều kiện kích hoạt | Còi Buzzer |
| :--- | :--- | :--- |
| **WARMING_UP** | 30 giây đầu sau khi bật nguồn | Tắt hoàn toàn |
| **SAFE** | `ADC < 1200` | Tắt |
| **WARNING** | `1200 <= ADC < 2200` | Kêu ngắt quãng (150ms mỗi 1.5s) |
| **DANGER** | `ADC >= 2200` hoặc chân `DO` kích hoạt | Hụ dồn dập (120ms ON / 120ms OFF) |

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
2. Mở thư mục `src/` trong PlatformIO hoặc Arduino IDE và nạp chương trình từ `src/main.cpp`.
3. Nhấn nút **Upload** và mở **Serial Monitor** ở tốc độ **115200 bps**.

### Cách 2: Nạp bằng PlatformIO
```bash
pio run -t upload
pio device monitor -b 115200
```

## 6. Wi-Fi, MQTT và GPS

Đặt `WIFI_SSID`, `WIFI_PASSWORD`, `MQTT_BROKER` và thông tin MQTT trong
[src/config.h](src/config.h) trước khi nạp. Firmware gửi:

- `helmet/{id}/telemetry` mỗi 2 giây, gồm giá trị khí và `lat`/`lng` khi NEO-6M có fix.
- `helmet/{id}/event` khi chuyển sang `DANGER`, với loại `GAS_DANGER` và tọa độ GPS.
- `helmet/{id}/status` với Last Will `{"online":false}`.

Mất Wi-Fi/MQTT không ảnh hưởng đến còi. Tối đa 8 event được giữ trong hàng đợi
RAM và gửi bù theo thứ tự khi kết nối lại; event `GAS_DANGER` không bị bỏ qua
khi publish thất bại.
