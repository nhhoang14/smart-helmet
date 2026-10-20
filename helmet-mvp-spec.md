# Hệ thống giám sát khí độc và phát hiện té ngã – Tài liệu MVP

Mũ bảo hộ thông minh (ESP32) gửi dữ liệu qua MQTT, backend xử lý và lưu trữ, web dashboard hiển thị realtime và cho phép điều khiển.

Phạm vi MVP: **1 mũ bảo hộ** (`device_id = 01`), nhưng mọi bảng và topic đều có `device_id` để mở rộng sau.

---

## 1. Công nghệ

| Lớp | Công nghệ | Vai trò |
|---|---|---|
| Thiết bị | ESP32 DevKit V1, Arduino framework, PubSubClient, ArduinoJson | Đọc cảm biến, publish MQTT |
| Broker | Eclipse Mosquitto (Docker) | Trung chuyển bản tin Publish-Subscribe |
| Backend | Java 17, Spring Boot 3, Spring Integration MQTT, Spring WebSocket (STOMP), Spring Data JPA, Flyway | Nhận MQTT, áp dụng luật cảnh báo, lưu DB, REST API, đẩy realtime |
| CSDL | PostgreSQL 16 | Lưu thiết bị, telemetry, cảnh báo, cấu hình, lệnh |
| Frontend | React 18, Vite, Recharts, Leaflet, @stomp/stompjs | Dashboard realtime |
| Thông báo ngoài | Telegram Bot API (webhook) | Đẩy cảnh báo ra điện thoại |
| Triển khai | Docker Compose | Chạy toàn bộ hệ thống |

---

## 2. Kiến trúc và luồng dữ liệu

```
 ┌──────────────┐  MQTT   ┌───────────┐  MQTT   ┌──────────────────────────┐
 │ ESP32 (mũ)   │ ──────► │ Mosquitto │ ──────► │ Spring Boot backend      │
 │ MQ-135       │         │  :1883    │ ◄────── │  - Luật cảnh báo         │
 │ MPU6050      │ ◄────── │           │  lệnh   │  - Lưu PostgreSQL        │
 │ GPS, Touch   │  lệnh   └───────────┘         │  - REST + WebSocket      │
 │ Nút SOS      │                               └───────┬───────────┬──────┘
 └──────────────┘                                       │           │
                                          REST/WebSocket│           │HTTPS
                                                ┌───────▼──────┐ ┌──▼────────┐
                                                │ React web    │ │ Telegram  │
                                                │ dashboard    │ │ Bot       │
                                                └──────────────┘ └───────────┘
```

Ba luồng chính:

1. **Telemetry**: ESP32 publish mỗi 1–2 giây, backend nhận, kiểm tra ngưỡng khí, lưu DB (lấy mẫu thưa), đẩy xuống web qua WebSocket.
2. **Sự kiện (té ngã, SOS)**: ESP32 tự phát hiện té ngã bằng TinyML với MPU6050, chỉ publish sự kiện. Backend tạo cảnh báo mức nguy hiểm, đẩy WebSocket và gửi Telegram.
3. **Lệnh điều khiển**: người dùng bấm "Bật còi" trên web, gọi REST, backend publish xuống `helmet/{id}/cmd`, ESP32 nhận và bật còi.

---

## 3. Giao thức MQTT

| Topic | Hướng | Tần suất | Payload mẫu |
|---|---|---|---|
| `helmet/{id}/telemetry` | Mũ → Server | 1–2 giây | `{"gas":132,"worn":true,"fall":false,"lat":21.0285,"lng":105.8542,"ts":1760950000}` |
| `helmet/{id}/event` | Mũ → Server | Khi xảy ra | `{"type":"fall","lat":21.0285,"lng":105.8542,"ts":1760950010}` (`type`: `fall` hoặc `sos`) |
| `helmet/{id}/status` | Mũ → Server | Khi kết nối/mất kết nối | `online` hoặc `offline` (retained, `offline` đặt làm Last Will) |
| `helmet/{id}/cmd` | Server → Mũ | Khi có lệnh | `{"buzzer":true,"cmdId":17,"ts":1760950020}` |

Ghi chú:
- `gas` là giá trị ppm quy đổi từ ADC của MQ-135 (cần hiệu chuẩn).
- Backend subscribe bằng wildcard `helmet/+/telemetry`, `helmet/+/event`, `helmet/+/status`.
- QoS đề xuất: telemetry QoS 0, event và cmd QoS 1.

---

## 4. Cơ sở dữ liệu (PostgreSQL)

### 4.1 Quan hệ giữa các bảng

```
devices 1 ──── 1 device_settings
   │
   ├── 1 ──── n telemetry
   ├── 1 ──── n alerts
   └── 1 ──── n device_commands

users 1 ──── n alerts            (acknowledged_by)
users 1 ──── n device_commands   (created_by)
```

### 4.2 Bảng `devices` – thiết bị

| Trường | Kiểu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | VARCHAR(32) | PK | Mã thiết bị, ví dụ `01` (dùng trong topic MQTT) |
| `name` | VARCHAR(100) | NOT NULL | Tên hiển thị, ví dụ "Mũ #01" |
| `zone` | VARCHAR(100) | | Khu vực làm việc |
| `is_online` | BOOLEAN | NOT NULL, DEFAULT false | Trạng thái kết nối hiện tại |
| `is_worn` | BOOLEAN | NOT NULL, DEFAULT false | Có đang được đội (touch pin) |
| `buzzer_on` | BOOLEAN | NOT NULL, DEFAULT false | Còi tìm mũ đang bật |
| `last_seen_at` | TIMESTAMPTZ | | Thời điểm nhận bản tin gần nhất |
| `last_lat` | DOUBLE PRECISION | | Vĩ độ gần nhất |
| `last_lng` | DOUBLE PRECISION | | Kinh độ gần nhất |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | Ngày tạo |

### 4.3 Bảng `device_settings` – cấu hình theo thiết bị

| Trường | Kiểu | Ràng buộc | Mô tả |
|---|---|---|---|
| `device_id` | VARCHAR(32) | PK, FK → `devices.id` | Quan hệ 1–1 |
| `gas_warn_ppm` | INTEGER | NOT NULL, DEFAULT 400 | Ngưỡng cảnh báo khí |
| `gas_danger_factor` | NUMERIC(3,1) | NOT NULL, DEFAULT 1.5 | Ngưỡng nguy hiểm = `gas_warn_ppm` × hệ số này |
| `offline_timeout_sec` | INTEGER | NOT NULL, DEFAULT 10 | Quá thời gian này không có bản tin thì coi là mất kết nối |
| `telemetry_sample_sec` | INTEGER | NOT NULL, DEFAULT 5 | Chu kỳ lưu telemetry vào DB (lấy mẫu thưa) |
| `telegram_chat_id` | VARCHAR(64) | | Chat nhận cảnh báo Telegram |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | Lần sửa cuối |

### 4.4 Bảng `telemetry` – dữ liệu cảm biến

| Trường | Kiểu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | BIGSERIAL | PK | |
| `device_id` | VARCHAR(32) | NOT NULL, FK → `devices.id` | |
| `gas_ppm` | NUMERIC(8,2) | NOT NULL | Nồng độ khí |
| `is_worn` | BOOLEAN | NOT NULL | Trạng thái đội mũ lúc đó |
| `fall_state` | BOOLEAN | NOT NULL, DEFAULT false | Có té ngã tại thời điểm đo |
| `lat` | DOUBLE PRECISION | | Vĩ độ |
| `lng` | DOUBLE PRECISION | | Kinh độ |
| `recorded_at` | TIMESTAMPTZ | NOT NULL | Thời điểm đo (lấy từ `ts` của thiết bị) |

Index: `(device_id, recorded_at DESC)`.
Gợi ý dung lượng: nếu lưu từng giây sẽ khoảng 86.000 dòng mỗi ngày, nên chỉ lưu theo `telemetry_sample_sec` và lưu thêm mọi mẫu vượt ngưỡng. Có thể thêm job xóa dữ liệu cũ hơn 30 ngày.

### 4.5 Bảng `alerts` – cảnh báo

| Trường | Kiểu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | BIGSERIAL | PK | |
| `device_id` | VARCHAR(32) | NOT NULL, FK → `devices.id` | |
| `type` | VARCHAR(20) | NOT NULL | `GAS_WARNING`, `GAS_DANGER`, `FALL`, `SOS`, `OFFLINE` |
| `severity` | VARCHAR(10) | NOT NULL | `WARNING` hoặc `DANGER` |
| `message` | VARCHAR(255) | NOT NULL | Nội dung hiển thị |
| `gas_ppm` | NUMERIC(8,2) | | Giá trị khí lúc xảy ra (chỉ với cảnh báo khí) |
| `lat` | DOUBLE PRECISION | | Vị trí lúc xảy ra |
| `lng` | DOUBLE PRECISION | | |
| `status` | VARCHAR(12) | NOT NULL, DEFAULT 'NEW' | `NEW`, `ACKNOWLEDGED` |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | Thời điểm phát sinh |
| `acknowledged_at` | TIMESTAMPTZ | | Thời điểm được xác nhận |
| `acknowledged_by` | BIGINT | FK → `users.id` | Người xác nhận (NULL nếu chưa có đăng nhập) |
| `telegram_sent` | BOOLEAN | NOT NULL, DEFAULT false | Đã gửi Telegram chưa |

Index: `(device_id, status, created_at DESC)`.

### 4.6 Bảng `device_commands` – lệnh gửi xuống mũ

| Trường | Kiểu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | BIGSERIAL | PK | Cũng là `cmdId` trong payload MQTT |
| `device_id` | VARCHAR(32) | NOT NULL, FK → `devices.id` | |
| `command_type` | VARCHAR(20) | NOT NULL | `BUZZER_ON`, `BUZZER_OFF` |
| `payload` | JSONB | NOT NULL | Nội dung đã publish |
| `status` | VARCHAR(10) | NOT NULL, DEFAULT 'SENT' | `SENT`, `FAILED` |
| `created_by` | BIGINT | FK → `users.id` | Người gửi lệnh |
| `sent_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

### 4.7 Bảng `users` – tài khoản (tùy chọn)

Bản MVP đầu có thể bỏ qua đăng nhập; thêm bảng này khi làm phân quyền.

| Trường | Kiểu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | BIGSERIAL | PK | |
| `username` | VARCHAR(50) | NOT NULL, UNIQUE | |
| `password_hash` | VARCHAR(100) | NOT NULL | BCrypt |
| `role` | VARCHAR(10) | NOT NULL, DEFAULT 'VIEWER' | `ADMIN` hoặc `VIEWER` |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

---

## 5. Backend – cấu trúc và chức năng từng file

```
helmet-backend/
├── pom.xml
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/com/example/helmet/
    │   │   ├── HelmetApplication.java
    │   │   ├── config/
    │   │   │   ├── MqttConfig.java
    │   │   │   ├── WebSocketConfig.java
    │   │   │   ├── CorsConfig.java
    │   │   │   └── SchedulingConfig.java
    │   │   ├── mqtt/
    │   │   │   ├── MqttInboundHandler.java
    │   │   │   └── MqttCommandPublisher.java
    │   │   ├── model/
    │   │   │   ├── Device.java
    │   │   │   ├── DeviceSettings.java
    │   │   │   ├── Telemetry.java
    │   │   │   ├── Alert.java
    │   │   │   ├── DeviceCommand.java
    │   │   │   ├── AlertType.java
    │   │   │   ├── AlertSeverity.java
    │   │   │   └── AlertStatus.java
    │   │   ├── repository/
    │   │   │   ├── DeviceRepository.java
    │   │   │   ├── DeviceSettingsRepository.java
    │   │   │   ├── TelemetryRepository.java
    │   │   │   ├── AlertRepository.java
    │   │   │   └── DeviceCommandRepository.java
    │   │   ├── service/
    │   │   │   ├── TelemetryService.java
    │   │   │   ├── AlertService.java
    │   │   │   ├── DeviceService.java
    │   │   │   ├── CommandService.java
    │   │   │   ├── SettingsService.java
    │   │   │   ├── OfflineWatchdog.java
    │   │   │   └── TelegramNotifier.java
    │   │   ├── web/
    │   │   │   ├── DeviceController.java
    │   │   │   ├── TelemetryController.java
    │   │   │   ├── AlertController.java
    │   │   │   ├── CommandController.java
    │   │   │   └── SettingsController.java
    │   │   ├── realtime/
    │   │   │   └── RealtimePublisher.java
    │   │   └── dto/
    │   │       ├── TelemetryMessage.java
    │   │       ├── EventMessage.java
    │   │       ├── DeviceStateDto.java
    │   │       ├── AlertDto.java
    │   │       ├── SettingsDto.java
    │   │       └── CommandRequest.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/V1__init.sql
    └── test/java/com/example/helmet/
        ├── AlertServiceTest.java
        └── MqttInboundHandlerTest.java
```

### 5.1 `config/`

| File | Chức năng |
|---|---|
| `MqttConfig.java` | Khai báo kết nối tới Mosquitto (URL, client id, username/password từ `application.yml`). Tạo `MqttPahoMessageDrivenChannelAdapter` subscribe `helmet/+/telemetry`, `helmet/+/event`, `helmet/+/status` và nối vào `MqttInboundHandler`. Tạo outbound channel cho `MqttCommandPublisher`. |
| `WebSocketConfig.java` | Bật STOMP: endpoint `/ws` (cho phép origin của frontend), broker đơn giản tiền tố `/topic`. |
| `CorsConfig.java` | Cho phép frontend gọi REST từ origin khác khi chạy dev. |
| `SchedulingConfig.java` | Bật `@EnableScheduling` cho `OfflineWatchdog`. |

### 5.2 `mqtt/`

| File | Chức năng |
|---|---|
| `MqttInboundHandler.java` | Điểm vào của mọi bản tin MQTT. Tách `device_id` từ topic, parse JSON thành DTO, rồi chuyển: telemetry → `TelemetryService`, event → `AlertService`, status → `DeviceService`. Bắt và log lỗi parse, không để một bản tin hỏng làm sập luồng. |
| `MqttCommandPublisher.java` | Publish lệnh xuống `helmet/{id}/cmd` (QoS 1). Trả kết quả thành công/thất bại cho `CommandService`. |

### 5.3 `model/` (entity JPA)

| File | Chức năng |
|---|---|
| `Device.java` | Ánh xạ bảng `devices`. |
| `DeviceSettings.java` | Ánh xạ bảng `device_settings`. Có hàm tiện ích `dangerThreshold()` = `gasWarnPpm × gasDangerFactor`. |
| `Telemetry.java` | Ánh xạ bảng `telemetry`. |
| `Alert.java` | Ánh xạ bảng `alerts`. |
| `DeviceCommand.java` | Ánh xạ bảng `device_commands`. |
| `AlertType.java` | Enum `GAS_WARNING`, `GAS_DANGER`, `FALL`, `SOS`, `OFFLINE`. |
| `AlertSeverity.java` | Enum `WARNING`, `DANGER`. |
| `AlertStatus.java` | Enum `NEW`, `ACKNOWLEDGED`. |

### 5.4 `repository/` (Spring Data JPA)

| File | Chức năng |
|---|---|
| `DeviceRepository.java` | CRUD thiết bị; tìm các thiết bị `is_online = true` có `last_seen_at` quá hạn (cho watchdog). |
| `DeviceSettingsRepository.java` | Đọc/ghi cấu hình theo `device_id`. |
| `TelemetryRepository.java` | Lưu telemetry; truy vấn theo khoảng thời gian (`findByDeviceIdAndRecordedAtBetween`); xóa dữ liệu cũ. |
| `AlertRepository.java` | Truy vấn cảnh báo theo trạng thái, loại, phân trang; tìm cảnh báo `NEW` gần nhất cùng loại để chống tạo trùng. |
| `DeviceCommandRepository.java` | Lưu và đọc lịch sử lệnh. |

### 5.5 `service/` (nghiệp vụ)

| File | Chức năng |
|---|---|
| `TelemetryService.java` | Nhận telemetry: cập nhật trạng thái hiện tại của `devices` (online, đội mũ, vị trí, `last_seen_at`); gọi `AlertService.evaluateGas()`; lưu vào `telemetry` theo chu kỳ `telemetry_sample_sec` (và luôn lưu khi vượt ngưỡng); gọi `RealtimePublisher` để đẩy xuống web. |
| `AlertService.java` | Trái tim của hệ thống cảnh báo. `evaluateGas()`: so sánh với ngưỡng, chỉ tạo cảnh báo khi **chuyển mức** (an toàn → cảnh báo → nguy hiểm) để không tạo hàng loạt cảnh báo trùng. `handleEvent()`: tạo cảnh báo `FALL` hoặc `SOS` mức `DANGER`. `createOffline()`: cảnh báo mất kết nối. `acknowledge()`: chuyển trạng thái `ACKNOWLEDGED`, ghi thời gian và người xác nhận. Mỗi cảnh báo mới đều gọi `RealtimePublisher` và `TelegramNotifier`. |
| `DeviceService.java` | Quản lý trạng thái thiết bị; xử lý bản tin `status` (online/offline); lấy trạng thái hiện tại cho dashboard. |
| `CommandService.java` | Nhận yêu cầu bật/tắt còi: lưu `device_commands`, gọi `MqttCommandPublisher`, cập nhật `buzzer_on`, cập nhật trạng thái lệnh `SENT`/`FAILED`. |
| `SettingsService.java` | Đọc và cập nhật `device_settings` (ngưỡng khí, timeout, Telegram). Kiểm tra giá trị hợp lệ (ví dụ ngưỡng 100–1000). |
| `OfflineWatchdog.java` | Job `@Scheduled` chạy mỗi 5 giây: tìm thiết bị `is_online` mà `last_seen_at` quá `offline_timeout_sec`, đặt `is_online = false`, tạo cảnh báo `OFFLINE`, đẩy realtime. |
| `TelegramNotifier.java` | Gửi tin nhắn cảnh báo tới `telegram_chat_id` bằng HTTP tới Telegram Bot API (token đọc từ biến môi trường). Lỗi mạng chỉ log, không ảnh hưởng luồng chính. |

### 5.6 `web/` (REST controller)

| File | Chức năng |
|---|---|
| `DeviceController.java` | `GET /api/devices` và `GET /api/devices/{id}`: trạng thái hiện tại của thiết bị. |
| `TelemetryController.java` | `GET /api/devices/{id}/telemetry?from=&to=`: dữ liệu cho biểu đồ lịch sử. |
| `AlertController.java` | `GET /api/alerts?status=&type=&page=`: danh sách cảnh báo; `POST /api/alerts/{id}/ack`: xác nhận đã xử lý. |
| `CommandController.java` | `POST /api/devices/{id}/buzzer` với body `{"on":true}`: bật/tắt còi. |
| `SettingsController.java` | `GET` và `PUT /api/devices/{id}/settings`: xem và chỉnh ngưỡng. |

### 5.7 `realtime/` và `dto/`

| File | Chức năng |
|---|---|
| `RealtimePublisher.java` | Dùng `SimpMessagingTemplate` đẩy dữ liệu xuống web: `/topic/devices/{id}/telemetry` (mỗi bản tin), `/topic/devices/{id}/state` (khi trạng thái đổi), `/topic/alerts` (khi có cảnh báo mới hoặc vừa được xác nhận). |
| `TelemetryMessage.java` | DTO cho payload telemetry từ MQTT (`gas`, `worn`, `fall`, `lat`, `lng`, `ts`). |
| `EventMessage.java` | DTO cho payload event (`type`, `lat`, `lng`, `ts`). |
| `DeviceStateDto.java` | Trạng thái hiện tại gửi cho web. |
| `AlertDto.java` | Cảnh báo gửi cho web (không lộ trường nội bộ). |
| `SettingsDto.java` | Cấu hình ngưỡng gửi/nhận qua REST. |
| `CommandRequest.java` | Body của yêu cầu điều khiển còi. |

### 5.8 `resources/` và test

| File | Chức năng |
|---|---|
| `application.yml` | Cấu hình DB, MQTT broker, cổng, token Telegram (qua biến môi trường), CORS origin. |
| `db/migration/V1__init.sql` | Flyway tạo toàn bộ bảng ở mục 4, index, và chèn sẵn thiết bị `01` cùng `device_settings` mặc định. |
| `AlertServiceTest.java` | Kiểm thử luật cảnh báo: chuyển mức đúng, không tạo trùng, xác nhận đúng. |
| `MqttInboundHandlerTest.java` | Kiểm thử parse topic và payload, xử lý payload lỗi. |

### 5.9 Bảng REST API và WebSocket

| Phương thức | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/api/devices/{id}` | Trạng thái hiện tại |
| GET | `/api/devices/{id}/telemetry?from=&to=` | Lịch sử khí |
| GET | `/api/alerts?status=&type=&page=` | Danh sách cảnh báo |
| POST | `/api/alerts/{id}/ack` | Xác nhận đã xử lý |
| POST | `/api/devices/{id}/buzzer` | Bật/tắt còi |
| GET / PUT | `/api/devices/{id}/settings` | Xem / chỉnh ngưỡng |
| WS | `/ws` (STOMP) | Kết nối realtime |
| Subscribe | `/topic/devices/{id}/telemetry` | Telemetry mới |
| Subscribe | `/topic/devices/{id}/state` | Trạng thái thiết bị đổi |
| Subscribe | `/topic/alerts` | Cảnh báo mới / đã xác nhận |

---

## 6. Frontend – cấu trúc và chức năng từng file

```
helmet-frontend/
├── package.json
├── vite.config.js
├── index.html
├── Dockerfile
└── src/
    ├── main.jsx
    ├── App.jsx
    ├── api/
    │   ├── client.js
    │   └── realtime.js
    ├── hooks/
    │   ├── useDeviceState.js
    │   ├── useTelemetryStream.js
    │   └── useAlerts.js
    ├── pages/
    │   ├── DashboardPage.jsx
    │   ├── AlertsPage.jsx
    │   ├── HistoryPage.jsx
    │   └── SettingsPage.jsx
    ├── components/
    │   ├── StatusBand.jsx
    │   ├── GasGauge.jsx
    │   ├── HelmetStatusCard.jsx
    │   ├── SiteMap.jsx
    │   ├── GasChart.jsx
    │   ├── AlertList.jsx
    │   ├── BuzzerButton.jsx
    │   └── ConnectionBadge.jsx
    ├── utils/
    │   ├── format.js
    │   └── alertSound.js
    └── styles/
        └── theme.css
```

### 6.1 Gốc ứng dụng

| File | Chức năng |
|---|---|
| `main.jsx` | Điểm khởi chạy, gắn `<App />` vào DOM. |
| `App.jsx` | Điều hướng giữa 4 trang (React Router), khung giao diện chung (tiêu đề, `ConnectionBadge`), khởi tạo kết nối realtime một lần. |
| `vite.config.js` | Cấu hình dev server, proxy `/api` và `/ws` tới backend. |

### 6.2 `api/`

| File | Chức năng |
|---|---|
| `client.js` | Các hàm gọi REST: `getDevice`, `getTelemetry`, `getAlerts`, `ackAlert`, `setBuzzer`, `getSettings`, `updateSettings`. Xử lý lỗi và URL gốc tại một chỗ. |
| `realtime.js` | Tạo client STOMP, kết nối `/ws`, tự kết nối lại khi rớt, cung cấp hàm `subscribe(topic, callback)` và trạng thái kết nối. |

### 6.3 `hooks/`

| File | Chức năng |
|---|---|
| `useDeviceState.js` | Tải trạng thái ban đầu bằng REST rồi cập nhật theo `/topic/devices/{id}/state`. Trả về `{online, worn, buzzer, fall, lastSeen, lat, lng}`. |
| `useTelemetryStream.js` | Giữ 60 mẫu gần nhất từ `/topic/devices/{id}/telemetry` cho đồng hồ và biểu đồ trực tiếp. |
| `useAlerts.js` | Tải danh sách cảnh báo, nghe `/topic/alerts`, cung cấp `acknowledge(id)`; phát âm thanh khi có cảnh báo nguy hiểm mới. |

### 6.4 `pages/`

| File | Chức năng |
|---|---|
| `DashboardPage.jsx` | Trang chính (giống bản mẫu): ghép `StatusBand`, `GasGauge`, `HelmetStatusCard`, `SiteMap`, `GasChart`, `AlertList`. |
| `AlertsPage.jsx` | Lịch sử cảnh báo có phân trang, lọc theo loại và trạng thái, xem vị trí xảy ra. |
| `HistoryPage.jsx` | Biểu đồ nồng độ khí theo khoảng thời gian tự chọn (gọi `getTelemetry`). |
| `SettingsPage.jsx` | Chỉnh ngưỡng cảnh báo, timeout mất kết nối, chat Telegram (gọi `getSettings`/`updateSettings`). |

### 6.5 `components/`

| File | Chức năng |
|---|---|
| `StatusBand.jsx` | Dải trạng thái lớn: An toàn / Cảnh báo (sọc vàng đen) / Nguy hiểm (đỏ), hiển thị cảnh báo nghiêm trọng nhất chưa xử lý và nút "Xác nhận đã xử lý". |
| `GasGauge.jsx` | Đồng hồ bán nguyệt SVG hiển thị ppm, đổi màu theo ngưỡng cảnh báo/nguy hiểm. |
| `HelmetStatusCard.jsx` | Danh sách trạng thái: kết nối, đội mũ, té ngã, còi, thời điểm cập nhật cuối. |
| `SiteMap.jsx` | Bản đồ Leaflet với OpenStreetMap, marker vị trí mũ, đổi màu khi có cảnh báo khí. |
| `GasChart.jsx` | Biểu đồ đường (Recharts) với đường ngưỡng cảnh báo. Dùng chung cho Dashboard (60 giây) và HistoryPage. |
| `AlertList.jsx` | Danh sách cảnh báo, mỗi dòng có thời gian, mức độ, nút "Xác nhận". Dòng đã xử lý làm mờ. |
| `BuzzerButton.jsx` | Nút bật/tắt còi tìm mũ, gọi `setBuzzer`, hiển thị trạng thái đang gửi lệnh. |
| `ConnectionBadge.jsx` | Huy hiệu cho biết web đang kết nối realtime hay mất kết nối với backend. |

### 6.6 `utils/` và `styles/`

| File | Chức năng |
|---|---|
| `format.js` | Định dạng giờ, ppm, toạ độ. |
| `alertSound.js` | Phát âm báo trên trình duyệt (cần người dùng tương tác trang ít nhất một lần mới được phát). |
| `theme.css` | Biến màu, kiểu chữ, hỗ trợ giao diện sáng/tối. |

### 6.7 Quan hệ với bản mẫu đã dựng

Bản dashboard mẫu một file tương ứng với React như sau:

| Phần trong bản mẫu | Thành phần React |
|---|---|
| Hàm `onTelemetry()` | `useTelemetryStream` |
| Hàm `addAlert()` và `ack()` | `useAlerts` |
| Dải `#band` | `StatusBand` |
| Đồng hồ `#arc` | `GasGauge` |
| Thẻ "Trạng thái mũ" | `HelmetStatusCard` |
| Khối SVG vị trí | `SiteMap` (thay bằng Leaflet) |
| Khối `#hist` | `GasChart` |
| Khung "Giả lập thiết bị" | Bỏ đi, thay bằng ESP32 thật hoặc script giả lập MQTT |

---

## 7. Triển khai

```
helmet-system/
├── docker-compose.yml
├── mosquitto/
│   └── mosquitto.conf
├── helmet-backend/
├── helmet-frontend/
└── firmware/
    └── helmet.ino
```

| File | Chức năng |
|---|---|
| `docker-compose.yml` | Chạy 4 dịch vụ: `mosquitto` (1883), `postgres` (5432, volume lưu dữ liệu), `backend` (8080), `frontend` (80 hoặc 5173). Biến môi trường: mật khẩu DB, token Telegram, địa chỉ broker. |
| `mosquitto/mosquitto.conf` | Cổng 1883, bật xác thực username/password, tắt `allow_anonymous`. |
| `firmware/helmet.ino` | Đọc MQ-135, MPU6050 (chạy mô hình TinyML), touch pin, GPS NEO-6M, nút SOS; kết nối Wi-Fi và MQTT; publish telemetry/event/status (đặt Last Will `offline`); subscribe `cmd` để điều khiển còi. |

---

## 8. Thứ tự triển khai đề xuất

1. **Hạ tầng**: `docker-compose.yml` với Mosquitto và PostgreSQL; chạy `V1__init.sql`.
2. **Giả lập thiết bị**: viết script publish telemetry giả lên `helmet/01/telemetry` (kiểm tra bằng MQTT Explorer) để làm việc khi chưa có phần cứng.
3. **Backend lõi**: `MqttConfig` → `MqttInboundHandler` → `TelemetryService` → lưu DB.
4. **Cảnh báo**: `AlertService` (chuyển mức khí, FALL, SOS), `OfflineWatchdog`, test đơn vị.
5. **Realtime**: `WebSocketConfig`, `RealtimePublisher`.
6. **REST**: các controller (thiết bị, cảnh báo, ack, cài đặt, còi) và `MqttCommandPublisher`.
7. **Frontend**: `realtime.js` + `DashboardPage` (StatusBand, GasGauge, HelmetStatusCard, AlertList), sau đó SiteMap, GasChart.
8. **Trang phụ**: AlertsPage, HistoryPage, SettingsPage.
9. **Telegram** và **firmware thật** (nối ESP32 thay script giả lập).
10. **Bổ sung nếu còn thời gian**: đăng nhập (bảng `users`), Web Push, nhiều thiết bị.

Điều kiện hoàn thành MVP: bật khí giả lập vượt ngưỡng thì dashboard đổi màu và hiện cảnh báo; gửi event `fall` thì có cảnh báo nguy hiểm và tin Telegram; tắt script giả lập thì xuất hiện cảnh báo mất kết nối; bấm "Bật còi" thì bản tin xuất hiện trên topic `helmet/01/cmd`.
