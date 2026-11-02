import serial, csv, sys, time

port, label, count, outfile = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
SKIP = 3
ser = serial.Serial(port, 115200, timeout=10)
n = 0
with open(outfile, "a", newline="") as f:
    w = csv.writer(f)
    while n < count + SKIP:
        line = ser.readline().decode(errors="ignore").strip()
        if not line.startswith("SCAN"):
            continue
        n += 1
        if n <= SKIP:
            print(f"bỏ scan {n}/{SKIP}")
            continue
        ts = time.time()
        for item in line.split(";")[1:]:
            bssid, rssi = item.split("=")
            w.writerow([ts, label, bssid, rssi])
        print(f"{label}: {n-SKIP}/{count}")