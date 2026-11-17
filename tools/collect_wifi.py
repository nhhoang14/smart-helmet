# Thu dữ liệu huấn luyện Wi-Fi fingerprint từ Serial của mũ (firmware chính, WIFI_SCAN_DEBUG_SERIAL=1).
#
#   python tools/collect_wifi.py COM3 <khu_vuc> <so_lan_quet> <file.csv>
#   vd: python tools/collect_wifi.py COM3 phong_301 30 helmet-backend/data/wifi_train.csv
#
# <khu_vuc> phải trùng data-zone trên dashboard: phong_301 hoặc hanh_lang.
# Mũ chỉ quét khi đã kết nối Wi-Fi (WIFI_SSID trong src/config.h), không cần MQTT.
# Ghi nối vào file: ts,label,bssid,rssi (đúng định dạng backend đọc).
import csv
import sys
import time

import serial

port, label, count, outfile = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
SKIP = 2   # bỏ vài lần quét đầu trong lúc người thu còn đứng sát mũ
ser = serial.Serial(port, 115200, timeout=15)
got = 0
with open(outfile, "a", newline="") as f:
    w = csv.writer(f)
    while got < count + SKIP:
        line = ser.readline().decode(errors="ignore").strip()
        if not line.startswith("SCAN;"):
            continue
        got += 1
        if got <= SKIP:
            print(f"bỏ lần quét {got}/{SKIP}")
            continue
        ts = time.time()
        n = 0
        for item in line.split(";")[1:]:
            if "=" not in item:
                continue
            bssid, rssi = item.split("=", 1)
            w.writerow([ts, label, bssid, int(rssi)])
            n += 1
        print(f"{label}: {got - SKIP}/{count}  ({n} AP)")
