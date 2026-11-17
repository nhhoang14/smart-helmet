Đặt file huấn luyện Wi-Fi fingerprint ở đây: wifi_train.csv (ts,label,bssid,rssi).
Thu bằng: python tools/collect_wifi.py COM3 phong_301 30 helmet-backend/data/wifi_train.csv
Sau khi thu thêm, nạp lại không cần khởi động backend: POST http://localhost:8080/api/wifi-fingerprint/reload
