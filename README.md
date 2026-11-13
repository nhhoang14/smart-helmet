# Smart Helmet – ESP32 Firmware & Giám Sát Mũ Bảo Hộ Thông Minh

Firmware ESP32 cho hệ thống mũ bảo hộ thông minh, tích hợp:
- Cảm biến chất lượng không khí & khí độc **MQ-135** (đọc số đo thật qua ADC và quy đổi PPM).
- Module định vị toàn cầu **GPS NEO-6M** (giao tiếp UART2).
- Cơ chế định vị lai: **Ưu tiên GPS NEO-6M ngoài trời**, tự động dự phòng sang **định vị Wi-Fi khi ở trong nhà**.
- **Còi báo động (Active Buzzer)** tự động kêu khi nồng độ khí vượt ngưỡng.
- **Công tắc khẩn cấp SOS / Công tắc hành trình KW11-3Z-2**: khi bấm, còi bíp phản hồi, lập tức lấy tọa độ vị trí, gửi sự kiện về backend và hiển thị cảnh báo tức thì trên Web Dashboard.
- Truyền thông thời gian thực qua **MQTT Broker** (Eclipse Mosquitto) và **WebSocket (STOMP)**.

---

## 1. Sơ đồ kết nối phần cứng (ESP32 DevKit V1)

| Ngoại vi | Chân ESP32 | Ghi chú |
|---|---|---|
| **MQ-135 (AO)** | GPIO34 (ADC1_CH6) | Đọc analog điện áp cảm biến khí |
| **MQ-135 (DO)** | GPIO35 | Digital interrupt / ngõ ra so sánh LM393 |
| **GPS NEO-6M (TX)** | GPIO16 (RX2) | Nhận dữ liệu NMEA baud 9600 |
| **GPS NEO-6M (RX)** | GPIO17 (TX2) | Gửi lệnh cấu hình NEO-6M |
| **Active Buzzer** | GPIO25 | Còi báo động (HIGH: kêu, LOW: tắt) |
| **Công tắc KW11 / SOS** | GPIO4 / GPIO27 | Nối GND, kích hoạt `INPUT_PULLUP` (ấn: mức LOW) |

---

## 2. Luồng hoạt động chính

1. **Hiển thị số đo thực tế trên Web**:
   - Cảm biến MQ-135 được đọc với bộ lọc chống nhiễu EMA (Exponential Moving Average) và quy đổi sang ppm.
   - ESP32 định kỳ gửi bản tin `telemetry` (mỗi 3 giây) lên backend qua MQTT.
   - Web Dashboard hiển thị realtime chỉ số khí (ppm), điện áp cảm biến, biểu đồ nồng độ khí và đường giới hạn cảnh báo.

2. **Cơ chế định vị thông minh (Outdoor GPS / Indoor Wi-Fi)**:
   - **Ngoài trời**: Hệ thống kiểm tra tín hiệu từ module GPS NEO-6M. Nếu có fix hợp lệ (`age < 10s`, `satellites >= 3`), ưu tiên sử dụng tọa độ GPS thực tế với nhãn `Định vị GPS (Ngoài trời - NEO-6M)`.
   - **Trong nhà / Mất sóng GPS**: Hệ thống tự động chuyển sang tọa độ dựa theo Wi-Fi với nhãn `Định vị Wi-Fi (Trong nhà)`.

3. **Cảnh báo vượt ngưỡng khí độc**:
   - Khi nồng độ khí $\ge 600$ ppm (hoặc vượt ngưỡng nguy hiểm ADC/DO): Còi báo động lập tức kích hoạt kêu dồn dập (120ms ON / 120ms OFF), hệ thống chuyển sang `DANGER` và gửi cảnh báo lên backend.
   - Khi nồng độ khí $\ge 300$ ppm: Còi bíp nhắc nhở cảnh báo `WARNING`.
   - Khi nồng độ khí giảm về mức an toàn: Còi tự động tắt.

4. **Kích hoạt công tắc (KW11 / SOS)**:
   - Khi công tắc được ấn (bấm nút trên mũ), còi phát âm thanh bíp xác nhận.
   - Firmware lấy ngay tọa độ hiện tại (GPS ngoài trời hoặc Wi-Fi trong nhà).
   - Bản tin sự kiện `SOS` được gửi ngay lập tức về backend qua MQTT topic `helmet/device/HELMET_001/event` và `helmet/device/HELMET_001/alert`.
   - Web Dashboard ngay lập tức hiển thị thông báo popup (Toast) nổi bật `🚨 CẢNH BÁO KHẨN CẤP: Nút khẩn cấp SOS trên mũ đã được bấm!`, cập nhật tọa độ vị trí và thêm vào danh sách cảnh báo gần đây.

---

## 3. Cấu hình và nạp Firmware

### Cấu hình Wi-Fi & MQTT
Mở tệp `src/config.h` và cập nhật thông số mạng:
```cpp
#define WIFI_SSID       "Tên_WiFi_Của_Bạn"
#define WIFI_PASSWORD   "Mật_Khẩu_WiFi"
#define MQTT_BROKER     "192.168.1.100" // IP máy tính chạy Mosquitto Broker / Backend
#define MQTT_PORT       1883
```

### Nạp Firmware qua PlatformIO
```bash
pio run -e esp32dev -t upload
pio device monitor -e esp32dev -b 115200
```
