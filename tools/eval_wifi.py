# Đo độ chính xác thật của Wi-Fi fingerprint: học bằng các file đầu, kiểm tra bằng file cuối
# (file cuối nên thu ở một buổi khác). Cùng thuật toán với WifiFingerprintService của backend.
#
#   python tools/eval_wifi.py train_buoi1.csv train_buoi2.csv test_buoi3.csv
import collections
import math
import sys

FLOOR, K = -90, 5


def usable(b):
    try:
        return int(b[:2], 16) & 2 == 0
    except ValueError:
        return False


def load(paths):
    scans = collections.OrderedDict()
    for p in paths:
        for line in open(p, encoding="utf-8"):
            parts = line.strip().split(",")
            if len(parts) < 4:
                continue
            ts, label, bssid, rssi = parts[0], parts[1], parts[2].upper(), parts[3]
            try:
                rssi = int(rssi)
            except ValueError:
                continue
            if usable(bssid):
                scans.setdefault((p, label, ts), {})[bssid] = rssi
    return [(k[1], v) for k, v in scans.items()]


def vec(scan, cols):
    return [max(scan.get(b, FLOOR), FLOOR) for b in cols]


*train_files, test_file = sys.argv[1:]
train, test = load(train_files), load([test_file])
cols = sorted({b for _, s in train for b in s})
X = [(lab, vec(s, cols)) for lab, s in train]
conf = collections.Counter()
nearest = []
for lab, s in test:
    x = vec(s, cols)
    d = sorted((math.dist(x, v), l) for l, v in X)
    votes = collections.Counter()
    for dist, l in d[:K]:
        votes[l] += 1 / (dist + 1e-6)
    conf[(lab, votes.most_common(1)[0][0])] += 1
    nearest.append(d[0][0])

ok = sum(v for (a, b), v in conf.items() if a == b)
print(f"Học: {len(train)} lần quét, kiểm tra: {len(test)} lần quét, {len(cols)} BSSID")
print(f"Độ chính xác: {ok / max(len(test), 1):.0%}")
for (a, b), v in sorted(conf.items()):
    print(f"  thật {a:12s} -> đoán {b:12s}: {v}")
nearest.sort()
print(f"Khoảng cách tới mẫu gần nhất (trung vị / p95): {nearest[len(nearest) // 2]:.1f} / {nearest[int(len(nearest) * 0.95)]:.1f}")
