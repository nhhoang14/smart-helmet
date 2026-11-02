import threading, collections, joblib, serial, numpy as np
from flask import Flask, jsonify

PORT = "COM3"
art = joblib.load("model.joblib")
model, bssids, THR = art["model"], art["bssids"], art["thr"]
idx = {b: i for i, b in enumerate(bssids)}

state = {"places": [str(c) for c in model.classes_], "label": None,
         "smooth": None, "conf": 0.0, "dist": 0.0, "known": 0, "ts": 0}
hist = collections.deque(maxlen=3)

def locate(scan):
    x = np.full(len(bssids), -100.0)
    known = 0
    for b, r in scan.items():
        if b in idx:
            x[idx[b]] = r
            known += 1
    proba = model.predict_proba([x])[0]
    j = int(proba.argmax())
    dist = float(model.kneighbors([x], n_neighbors=1)[0][0][0])
    return str(model.classes_[j]), float(proba[j]), dist, known

def reader():
    try:
        ser = serial.Serial(PORT, 115200, timeout=10)
    except Exception as e:
        print("Không mở được", PORT, "->", e, "(đóng Serial Monitor chưa?)")
        return
    while True:
        line = ser.readline().decode(errors="ignore").strip()
        if not line.startswith("SCAN"):
            continue
        scan = {}
        for item in line.split(";")[1:]:
            try:
                b, r = item.split("=")
                scan[b] = int(r)
            except ValueError:
                pass
        label, conf, dist, known = locate(scan)
        if dist > THR:
            label = "khong_chac"
        hist.append(label)
        smooth = collections.Counter(hist).most_common(1)[0][0]
        state.update(label=label, smooth=smooth, conf=conf, dist=dist,
                     known=known, ts=__import__("time").time())
        print(label, f"(mượt: {smooth}, tin cậy {conf:.0%}, khoảng cách {dist:.1f})")

PAGE = """<!doctype html><html lang="vi"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Định vị ESP32</title>
<style>body{font-family:sans-serif;max-width:640px;margin:2rem auto;padding:0 1rem}
.p{display:inline-block;margin:.3rem;padding:1.2rem 1.5rem;border:2px solid #aaa;border-radius:10px;opacity:.5}
.on{border-color:#1a7f37;background:#dafbe1;color:#000;opacity:1;font-weight:bold}
#info{margin-top:1rem;color:#666}</style>
<h2>Vị trí hiện tại</h2><div id="places"></div><div id="info">đang chờ dữ liệu…</div>
<script>
async function tick(){
 try{
  const r=await (await fetch('/api/location')).json();
  document.getElementById('places').innerHTML=
    r.places.map(p=>`<span class="p ${p===r.smooth?'on':''}">${p}</span>`).join('');
  document.getElementById('info').textContent = r.ts
    ? `Tức thời: ${r.label} · tin cậy ${(r.conf*100).toFixed(0)}% · khoảng cách ${r.dist.toFixed(1)} · ${r.known} AP đã biết · ${Math.round(Date.now()/1000-r.ts)} giây trước`
    : 'đang chờ dữ liệu…';
 }catch(e){}
}
setInterval(tick,1000);tick();
</script></html>"""

app = Flask(__name__)

@app.route("/")
def home():
    return PAGE

@app.route("/api/location")
def api():
    return jsonify(state)

if __name__ == "__main__":
    threading.Thread(target=reader, daemon=True).start()
    app.run(host="0.0.0.0", port=5000, debug=False)