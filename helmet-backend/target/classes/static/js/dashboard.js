(function () {
    const device = document.body.dataset.device;
    if (!device || !window.WebSocket) return;
    const airQualityValues = [];
    const chart = document.getElementById('air-quality-chart');
    const chartContainer = document.querySelector('.air-quality-chart-container');
    const chartEmpty = document.getElementById('air-quality-empty');
    const airQualityNumber = document.getElementById('air-quality-number');
    const thresholdLine = document.getElementById('threshold-dashed-line');
    const thresholdBadge = document.getElementById('threshold-pill-badge');

    // ── Alert count badge ────────────────────────────────────────────────────
    let alertCount = parseInt(document.getElementById('alert-count-stat')?.textContent || '0', 10) || 0;
    function setAlertCount(n) {
        alertCount = Math.max(0, n);
        const stat = document.getElementById('alert-count-stat');
        if (stat) stat.textContent = alertCount;
        const total = document.getElementById('alert-total-count');
        if (total) total.textContent = alertCount;
        const badge = document.getElementById('nav-alert-badge');
        if (badge) badge.textContent = alertCount > 0 ? alertCount : '';
    }
    setAlertCount(alertCount);

    // ── Online status & last seen ────────────────────────────────────────────
    function updateOnlineStatus(online) {
        const dot = document.getElementById('dot-online');
        const text = document.getElementById('status-online-text');
        if (dot) dot.classList.toggle('offline', !online);
        if (text) text.textContent = online ? 'Online' : 'Offline';
    }

    function updateLastSeen() {
        const el = document.getElementById('last-seen-text');
        if (!el) return;
        const now = new Date();
        const pad = (n) => String(n).padStart(2, '0');
        el.textContent = `${pad(now.getDate())}/${pad(now.getMonth() + 1)}/${now.getFullYear()} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
    }

    // ── Fall detection card ───────────────────────────────────────────────────
    function updateFallCard(isFall, timeStr) {
        const card = document.getElementById('fall-card');
        const status = document.getElementById('fall-status');
        const timeEl = document.getElementById('fall-time');
        const iconPill = document.getElementById('fall-icon-pill');
        if (!card) return;
        if (isFall) {
            card.classList.add('fall-danger');
            if (status) {
                status.textContent = 'Phát hiện té ngã';
                status.className = 'stat-primary-text text-danger';
            }
            if (iconPill) iconPill.className = 'stat-icon-pill icon-red';
            if (timeEl) {
                timeEl.textContent = timeStr || 'Vừa xảy ra';
                timeEl.style.display = 'block';
            }
        } else {
            card.classList.remove('fall-danger');
            if (status) {
                status.textContent = 'Bình thường';
                status.className = 'stat-primary-text text-green';
            }
            if (iconPill) iconPill.className = 'stat-icon-pill icon-green';
            if (timeEl) timeEl.style.display = 'none';
        }
    }

    // ── Air quality chart ─────────────────────────────────────────────────────
    function drawAirQuality() {
        if (!chart) return;
        const width = chart.clientWidth;
        const height = chart.clientHeight;
        const ratio = window.devicePixelRatio || 1;
        chart.width = width * ratio;
        chart.height = height * ratio;
        const context = chart.getContext('2d');
        context.scale(ratio, ratio);
        context.clearRect(0, 0, width, height);

        const warnThresh = Number(chartContainer?.dataset.warn || 450);
        const min = airQualityValues.length ? Math.max(0, Math.min(...airQualityValues) - 30) : 0;
        const max = airQualityValues.length
            ? Math.max(min + 120, Math.max(...airQualityValues) + 40, warnThresh + 60)
            : Math.max(warnThresh + 60, 200);

        // Position threshold dashed line & badge
        if (thresholdLine && height > 0) {
            const posY = height - ((warnThresh - min) / (max - min)) * (height - 24) - 12;
            const clampedY = Math.max(16, Math.min(height - 10, posY));
            thresholdLine.style.top = `${clampedY}px`;
            if (thresholdBadge) {
                thresholdBadge.style.top = `${Math.max(6, clampedY - 26)}px`;
            }
        }

        if (!airQualityValues.length) return;
        const step = width / Math.max(airQualityValues.length - 1, 1);
        context.beginPath();
        airQualityValues.forEach((value, index) => {
            const x = index * step;
            const y = height - ((value - min) / (max - min)) * (height - 24) - 12;
            index ? context.lineTo(x, y) : context.moveTo(x, y);
        });
        context.strokeStyle = '#1e3a8a';
        context.lineWidth = 2.4;
        context.lineJoin = 'round';
        context.lineCap = 'round';
        context.stroke();

        // Subtle gradient fill under the line
        const fillGradient = context.createLinearGradient(0, 0, 0, height);
        fillGradient.addColorStop(0, 'rgba(30, 58, 138, 0.12)');
        fillGradient.addColorStop(1, 'rgba(30, 58, 138, 0.01)');
        context.lineTo((airQualityValues.length - 1) * step, height);
        context.lineTo(0, height);
        context.closePath();
        context.fillStyle = fillGradient;
        context.fill();
    }

    function updateAirQuality(data) {
        const sensors = data.sensors || {};
        const raw = data.gasPpm ?? data.gas ?? sensors.gas_ppm ?? sensors.gas;
        const value = Number(raw);
        if (!Number.isFinite(value) || value <= 0) return;
        airQualityValues.push(value);
        if (airQualityValues.length > 30) airQualityValues.shift();
        if (airQualityNumber) airQualityNumber.textContent = Math.round(value);
        if (chartEmpty) chartEmpty.hidden = true;

        const gasStat = document.getElementById('gas-stat');
        if (gasStat) gasStat.textContent = Math.round(value);

        const warnThresh = Number(chartContainer?.dataset.warn || 450);
        const gasLabel = document.getElementById('gas-label');
        if (gasLabel) {
            const isWarn = value >= warnThresh;
            gasLabel.textContent = isWarn ? 'Vượt ngưỡng' : 'An toàn';
            gasLabel.className = isWarn ? 'text-warn' : 'text-safe';
        }

        drawAirQuality();
    }

    // ── Toast notifications ───────────────────────────────────────────────────
    function showToast(message, type) {
        let container = document.getElementById('toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toast-container';
            container.className = 'toast-container';
            document.body.appendChild(container);
        }
        const toast = document.createElement('div');
        toast.className = `toast-item ${type || 'warning'}`;
        toast.innerHTML = `<span class="toast-icon">${type === 'danger' ? '🚨' : '⚠️'}</span><div class="toast-body"><strong>${type === 'danger' ? 'CẢNH BÁO KHẨN CẤP' : 'THÔNG BÁO'}</strong><p>${message}</p></div>`;
        container.appendChild(toast);
        setTimeout(() => {
            toast.classList.add('fade-out');
            setTimeout(() => toast.remove(), 400);
        }, 7000);
    }

    // ── Prepend alert row ─────────────────────────────────────────────────────
    function prependAlert(data) {
        const list = document.getElementById('recent-alerts-list');
        if (!list) return;
        const empty = document.getElementById('recent-alerts-empty');
        if (empty) empty.hidden = true;
        const row = document.createElement('div');
        row.className = 'alert-row';
        const now = new Date();
        const pad = (n) => String(n).padStart(2, '0');
        const dateStr = `${pad(now.getDate())}/${pad(now.getMonth() + 1)}/${now.getFullYear()}`;
        const timeStr = `${pad(now.getHours())}:${pad(now.getMinutes())}`;
        const devUid = data.deviceUid || device;
        const msg = data.message || 'Cảnh báo mới';

        row.dataset.type = data.type || data.alert_type || '';
        row.dataset.device = devUid;
        row.dataset.date = dateStr;
        row.dataset.id = data.id || '';
        row.dataset.msg = msg;
        row.dataset.time = timeStr;

        row.innerHTML = `
            <div class="alert-icon-square">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#d97706" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                    <line x1="12" y1="9" x2="12" y2="13"/>
                    <line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
            </div>
            <div class="alert-text-content">
                <strong>${msg} · ${devUid}</strong>
                <small>${dateStr} · ${timeStr}</small>
            </div>
            ${data.id ? `<button class="alert-ack-btn" data-ack-ids="${data.id}" data-ack-count="1">Xác nhận</button>` : ''}
        `;
        list.prepend(row);
        setAlertCount(alertCount + 1);
        groupAlerts();
    }

    // ── Alert grouping ────────────────────────────────────────────────────────
    function groupAlerts() {
        const list = document.getElementById('recent-alerts-list');
        if (!list) return;
        const rows = Array.from(list.querySelectorAll('.alert-row[data-type]'));
        if (!rows.length) return;

        // Group by type + device + date
        const groups = {};
        rows.forEach((row) => {
            const key = `${row.dataset.type}|${row.dataset.device}|${row.dataset.date}`;
            if (!groups[key]) {
                groups[key] = {
                    msg: row.dataset.msg,
                    device: row.dataset.device,
                    date: row.dataset.date,
                    times: [],
                    ids: []
                };
            }
            if (row.dataset.time) groups[key].times.push(row.dataset.time);
            if (row.dataset.id) groups[key].ids.push(row.dataset.id);
        });

        rows.forEach((r) => r.remove());

        Object.entries(groups).forEach(([, g]) => {
            const count = g.ids.length || g.times.length || 1;
            const div = document.createElement('div');
            div.className = 'alert-row';

            // Calculate time display: "13/11/2026 · 17:22 – 17:28" or "13/11/2026 · 17:22"
            let timeDisplay = `${g.date}`;
            if (g.times.length > 0) {
                const sortedTimes = [...g.times].sort();
                const minTime = sortedTimes[0];
                const maxTime = sortedTimes[sortedTimes.length - 1];
                if (count > 1 && minTime !== maxTime) {
                    timeDisplay += ` · ${minTime} – ${maxTime}`;
                } else {
                    timeDisplay += ` · ${minTime}`;
                }
            }

            const title = `${g.msg} · ${g.device}${count > 1 ? ` ×${count}` : ''}`;

            div.innerHTML = `
                <div class="alert-icon-square">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#d97706" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                        <line x1="12" y1="9" x2="12" y2="13"/>
                        <line x1="12" y1="17" x2="12.01" y2="17"/>
                    </svg>
                </div>
                <div class="alert-text-content">
                    <strong>${title}</strong>
                    <small>${timeDisplay}</small>
                </div>
                ${g.ids.length ? `<button class="alert-ack-btn" data-ack-ids="${g.ids.join(',')}" data-ack-count="${count}">Xác nhận</button>` : ''}
            `;
            list.appendChild(div);
        });

        // Attach click handlers
        list.querySelectorAll('button[data-ack-ids]').forEach((btn) => {
            btn.addEventListener('click', async function () {
                const ids = this.dataset.ackIds.split(',').filter(Boolean);
                const cnt = parseInt(this.dataset.ackCount, 10) || ids.length;
                this.disabled = true;
                this.textContent = 'Đang xử lý...';
                for (const id of ids) {
                    try { await fetch(`/alerts/${id}/ack`, { method: 'POST' }); } catch (_) {}
                }
                this.closest('.alert-row').remove();
                setAlertCount(alertCount - cnt);
                if (!list.querySelector('.alert-row')) {
                    const empty = document.getElementById('recent-alerts-empty');
                    if (empty) empty.hidden = false;
                }
            });
        });
    }

    groupAlerts();

    const initialGas = Number(airQualityNumber?.textContent);
    if (Number.isFinite(initialGas) && initialGas > 0) updateAirQuality({ gasPpm: initialGas });
    drawAirQuality();
    window.addEventListener('resize', drawAirQuality);

    // ── Sơ đồ vị trí trong nhà (khu vực từ Wi-Fi fingerprint) ─────────────────
    // Backend gửi trong bản state: zone, zone_source, zone_confidence, zone_rssi, zone_updated_at (epoch ms).
    // Quá ZONE_STALE_MS không có bản tin vị trí mới thì coi là mất tín hiệu.
    const ZONE_STALE_MS = 15000;
    const floorPlan = document.getElementById('floor-plan');
    const zoneNameEl = document.getElementById('zone-name');
    const zoneDetailEl = document.getElementById('zone-detail');
    let currentZone = floorPlan?.dataset.zone || 'unknown';
    let zoneSource = floorPlan?.dataset.source || '';
    let zoneRssi = null;
    let zoneConfidence = null;
    let lastZoneStamp = floorPlan?.dataset.zoneUpdated ? Number(floorPlan.dataset.zoneUpdated) : null;
    let lastZoneReceivedAt = null; // tính theo đồng hồ trình duyệt để không lệch giờ với server
    const initialZoneAge = Number(floorPlan?.dataset.zoneAge);
    if (lastZoneStamp !== null && Number.isFinite(initialZoneAge) && initialZoneAge >= 0) {
        lastZoneReceivedAt = Date.now() - initialZoneAge;
    }

    function zoneLabel(zone) {
        if (!floorPlan || !zone) return null;
        const area = floorPlan.querySelector(`.zone-area[data-zone="${CSS.escape(zone)}"]`);
        return area ? area.dataset.label : null;
    }

    function renderZone() {
        if (!floorPlan) return;
        const age = lastZoneReceivedAt === null ? null : Date.now() - lastZoneReceivedAt;
        const fresh = age !== null && age < ZONE_STALE_MS;
        const label = zoneLabel(currentZone);
        const active = fresh && label ? currentZone : null;

        floorPlan.querySelectorAll('[data-zone]').forEach((el) => {
            if (el === floorPlan) return;
            el.classList.toggle('is-active', el.dataset.zone === active);
        });
        floorPlan.classList.toggle('is-stale', !fresh);
        // Đưa khu vực đang sáng lên trên để viền đỏ không bị tường khu vực bên cạnh che
        const activeArea = active ? floorPlan.querySelector(`.zone-area[data-zone="${CSS.escape(active)}"]`) : null;
        const door = floorPlan.querySelector('.floor-door');
        if (activeArea && door && activeArea.nextElementSibling !== door) {
            door.parentNode.insertBefore(activeArea, door);
        }

        if (zoneNameEl) {
            if (age === null) zoneNameEl.textContent = 'Chưa có dữ liệu vị trí';
            else if (!fresh) zoneNameEl.textContent = 'Mất tín hiệu vị trí';
            else zoneNameEl.textContent = label || 'Chưa xác định khu vực';
        }
        if (zoneDetailEl) {
            const parts = [zoneSource === 'simulation' ? 'Dữ liệu giả lập' : 'Định vị Wi-Fi fingerprint'];
            if (fresh && typeof zoneConfidence === 'number') {
                parts.push(`tin cậy ${Math.round(zoneConfidence * 100)}%`);
            }
            if (zoneRssi && typeof zoneRssi === 'object') {
                const rssiText = Object.entries(zoneRssi)
                    .filter(([, v]) => Number(v) > -100)
                    .map(([k, v]) => `${k} ${v} dBm`)
                    .join(', ');
                if (rssiText) parts.push(rssiText);
            }
            if (age !== null) {
                const sec = Math.round(age / 1000);
                parts.push(sec <= 1 ? 'vừa cập nhật' : (fresh ? `${sec} giây trước` : `lần cuối ${sec} giây trước`));
            }
            zoneDetailEl.textContent = parts.join(' · ');
        }
    }

    function updateZone(data) {
        if (typeof data.zone !== 'string') return;
        const stamp = data.zone_updated_at ?? data.zoneUpdatedAt ?? null;
        // Bản state phát kèm telemetry lặp lại zone cũ: chỉ coi là mới khi mốc thời gian đổi
        if (stamp !== null && stamp === lastZoneStamp) return;
        lastZoneStamp = stamp;
        lastZoneReceivedAt = Date.now();
        currentZone = data.zone;
        zoneSource = data.zone_source ?? data.zoneSource ?? '';
        zoneRssi = data.zone_rssi ?? data.zoneRssi ?? null;
        zoneConfidence = data.zone_confidence ?? data.zoneConfidence ?? null;
        renderZone();
    }

    renderZone();
    setInterval(renderZone, 1000);

    // ── WebSocket ─────────────────────────────────────────────────────────────
    const socket = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`);
    socket.addEventListener('open', function () {
        socket.send('CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\0');
        socket.send(`SUBSCRIBE\nid:helmet-dashboard\ndestination:/topic/device/${device}\n\n\0`);
        socket.send(`SUBSCRIBE\nid:helmet-alerts\ndestination:/topic/alerts\n\n\0`);
    });

    socket.addEventListener('message', function (event) {
        const body = event.data.split('\n\n')[1];
        if (!body) return;
        try {
            const data = JSON.parse(body.replace(/\0$/, ''));

            // 1. Biểu đồ & nồng độ khí
            updateAirQuality(data);

            // 2. Trạng thái Online & thời gian cập nhật
            if (typeof data.isOnline === 'boolean' || typeof data.status === 'string') {
                const online = data.isOnline ?? data.status === 'ONLINE';
                updateOnlineStatus(online);
            }
            updateLastSeen();

            // 3. Tình trạng đội mũ
            if (typeof data.isWorn === 'boolean') {
                const wornEl = document.getElementById('worn-stat');
                const subEl = document.getElementById('worn-subtext');
                if (wornEl) wornEl.textContent = data.isWorn ? 'Đang đội' : 'Không đội';
                if (subEl) subEl.textContent = data.isWorn ? 'Cảm biến đội mũ hoạt động' : 'Chưa đội mũ';
            }

            // 4. Phát hiện té ngã
            const isFall = data.type === 'FALL' || data.alert_type === 'FALL' || (data.alertLevel === 'DANGER' && String(data.type).includes('FALL'));
            if (isFall) {
                const now = new Date();
                const pad = (n) => String(n).padStart(2, '0');
                const timeStr = `${pad(now.getDate())}/${pad(now.getMonth() + 1)}/${now.getFullYear()} ${pad(now.getHours())}:${pad(now.getMinutes())}`;
                updateFallCard(true, timeStr);
            }

            // 5. Vị trí trong nhà theo khu vực (phòng 301 / hành lang)
            updateZone(data);

            // 6. Trạng thái còi
            if (typeof data.buzzerOn === 'boolean') {
                const subtext = document.getElementById('buzzer-subtext');
                if (subtext) subtext.textContent = data.buzzerOn ? 'Đang phát âm thanh' : 'Đang tắt';
            }

            // 7. Toast & prepend cảnh báo
            if (data.message && (data.alert_type || data.type || data.severity)) {
                const isDanger = data.severity === 'DANGER' || String(data.type || '').includes('DANGER') || String(data.alert_type || '').includes('DANGER') || data.type === 'SOS';
                showToast(data.message, isDanger ? 'danger' : 'warning');
                prependAlert(data);
            }
        } catch (_) {
            // Ignore STOMP control frames and malformed messages.
        }
    });
}());
