# MQTT broker cục bộ

Project chứa Mosquitto tại `infra/mosquitto.conf` và service `mqtt` trong
`compose.yml`. Docker Desktop cần chạy. Broker dùng TCP1883; cấu hình phát
triển hiện tại không yêu cầu username/password, chỉ dùng trên mạng test riêng.

Chạy riêng broker:

```powershell
powershell -ExecutionPolicy Bypass -File D:\IOT\smart-helmet-synced\scripts\run-mqtt.ps1
```

Hoặc chạy backend cùng MQTT/MySQL:

```powershell
powershell -ExecutionPolicy Bypass -File D:\IOT\smart-helmet-synced\scripts\run-web.ps1
```

Tên Docker project và volume được giữ là `github-smart-helmet` để dùng lại tài
khoản/dữ liệu đã có. Các lệnh này không phụ thuộc thư mục project cũ.

ESP32 kết nối Wi-Fi `nhhoang` (2,4 GHz), dùng broker `10.72.117.44`, port1883 trong
`src/local_config.h`. Backend trong Docker dùng `tcp://mqtt:1883`; đây là cùng
một broker, truy cập bằng địa chỉ phù hợp từng phía.

| Topic | Vai trò |
| --- | --- |
| `helmet/device/HELMET_001/telemetry` | Dữ liệu thật từ ESP32 |
| `helmet/device/HELMET_001/alert` | Sự kiện khí/ngã/SOS từ mũ thật |
| `helmet/device/HELMET_001/status` | Trạng thái online/offline của mũ thật |
| `helmet/device/HELMET_001/command` | Backend gửi lệnh xuống ESP32 |

HELMET_002 được simulator tạo riêng ở backend; không ghi số đo giả vào topic
hoặc trạng thái của HELMET_001. Firmware chỉ online khi vào được Wi-Fi và log
`MQTT: connected.` xuất hiện. ESP32 và máy chạy broker phải cùng mạng. Nếu IP
máy thay đổi thì cập nhật broker trong cấu hình local và nạp lại ESP32.

Mở PowerShell bằng **Run as administrator** và chạy một lần cho mạng hiện tại:

```powershell
powershell -ExecutionPolicy Bypass -File D:\IOT\smart-helmet-synced\scripts\allow-mqtt-lan.ps1
```

Công tắc đội mũ dùng `D14–GND`: đóng công tắc là đang đội, mở là chưa đội.
Nút xác nhận dùng `D27–GND`: bấm một lần tắt còi, xác nhận cảnh báo ngã trên
server và giao diện. Chỉ cảnh báo ngã được kết thúc; cảnh báo khí độc còn tồn
tại vẫn giữ mức nguy hiểm tương ứng.

Firmware gửi `FALL_ACK` trên topic `alert`, kèm `related_fall_id` của lần ngã.
Backend xử lý đây là xác nhận, không tạo một cảnh báo mới. Telemetry lặp lại
`acknowledged_fall_id` để đồng bộ lại nếu mất gói/kết nối; ID cũ không được
xóa một lần ngã mới. Lịch sử được giữ với trạng thái `ACKNOWLEDGED`.

`worn_known`, `gas_ready`, `gas_warmup_remaining`, `fall_state` được giữ trong
state để web hiển thị đúng. Khi MQ-135 đang làm nóng, web báo đang làm nóng
thay vì hiển thị giá trị 0 an toàn. Sau làm nóng, vẫn gửi số đo khí khi tháo mũ;
điều kiện kích hoạt cảnh báo cục bộ vẫn phụ thuộc công tắc đội mũ như trước.

## Tự kết nối khi cấp nguồn

Firmware `INTEGRATED_V2_AUTOCONNECT` bắt đầu `WiFi.begin()` ngay khi task mạng
khởi động, không cần gõ lệnh Serial, bật công tắc đội mũ hay chờ MQ-135 sấy xong.
Wi-Fi station được khởi tạo một lần, bật auto reconnect và tắt Wi-Fi sleep.
Nếu chưa kết nối được, thử lại sau 20 giây; MQTT thử ngay khi Wi-Fi có IP và
thử lại mỗi 5 giây khi broker chưa sẵn sàng. Kết nối cũ được đóng khi Wi-Fi mất.

Khi MQTT kết nối thành công, mũ đăng ký topic command, phát ONLINE và telemetry
ngay cho `HELMET_001`, sau đó phát định kỳ 3 giây hoặc khi trạng thái thay đổi.
Mỗi 10 giây Serial in `NETWORK:` để phân biệt lỗi Wi-Fi và lỗi MQTT. Không in
mật khẩu. Banner khởi động mới giúp nhận biết board đã nạp đúng bản firmware.

Broker/backend trên máy phải đang chạy, đúng IP và cùng mạng với ESP32.
Đã build thành công và kiểm tra TCP broker LAN; chưa xác nhận Wi-Fi trên board
vì máy hiện chưa nhận cổng USB ESP32.
