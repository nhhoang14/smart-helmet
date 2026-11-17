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
        const raw = data.gas_ppm ?? data.gasPpm ?? data.gas ?? sensors.gas_ppm ?? sensors.gas;
        if (data.gas_ready === false) {
            const text = `Đang làm nóng cảm biến (${data.gas_warmup_remaining ?? 0}s)`;
            if (airQualityNumber) airQualityNumber.textContent = '—';
            const stat = document.getElementById('gas-stat');
            const label = document.getElementById('gas-label');
            if (stat) stat.textContent = '—';
            if (label) { label.textContent = text; label.className = 'text-warn'; }
            if (chartEmpty) { chartEmpty.hidden = false; chartEmpty.textContent = text; }
            return;
        }
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

    // ── Quản lý danh sách cảnh báo (alertItems) ─────────────────────────────
    let alertItems = [];

    // Khởi tạo alertItems từ các phần tử do Thymeleaf render sẵn
    const initialRows = document.querySelectorAll('#recent-alerts-list .alert-row[data-id]');
    initialRows.forEach((row) => {
        const id = row.dataset.id;
        const status = row.dataset.status || 'NEW';
        if (id && status === 'NEW') {
            alertItems.push({
                id: String(id),
                type: row.dataset.type || 'ALERT',
                device: row.dataset.device || device,
                date: row.dataset.date || '',
                time: row.dataset.time || '',
                msg: row.dataset.msg || 'Cảnh báo',
                severity: row.dataset.severity || 'WARNING'
            });
        }
    });

    function renderGroupedAlerts() {
        updateFallCard(alertItems.some(item => item.type === 'FALL'));
        const list = document.getElementById('recent-alerts-list');
        const empty = document.getElementById('recent-alerts-empty');
        if (!list) return;

        list.innerHTML = '';

        if (!alertItems.length) {
            if (empty) empty.hidden = false;
            setAlertCount(0);
            return;
        }

        if (empty) empty.hidden = true;
        setAlertCount(alertItems.length);

        // Group by type + device + date
        const groups = {};
        alertItems.forEach((item) => {
            const key = `${item.type}|${item.device}|${item.date}`;
            if (!groups[key]) {
                groups[key] = {
                    msg: item.msg,
                    device: item.device,
                    date: item.date,
                    times: [],
                    ids: []
                };
            }
            if (item.time) groups[key].times.push(item.time);
            if (item.id) groups[key].ids.push(item.id);
        });

        Object.entries(groups).forEach(([, g]) => {
            const count = g.ids.length || g.times.length || 1;
            const div = document.createElement('div');
            div.className = 'alert-row';

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
                this.disabled = true;
                this.textContent = 'Đang xử lý...';
                for (const id of ids) {
                    try { await fetch(`/alerts/${id}/ack`, { method: 'POST' }); } catch (_) { }
                }
                alertItems = alertItems.filter(it => !ids.includes(String(it.id)));
                renderGroupedAlerts();
            });
        });
    }

    function removeAlertById(id) {
        const idStr = String(id);
        const prevLen = alertItems.length;
        alertItems = alertItems.filter(it => String(it.id) !== idStr);
        if (alertItems.length !== prevLen) {
            renderGroupedAlerts();
        }
    }

    function prependAlert(data) {
        const idStr = data.id ? String(data.id) : '';
        if (idStr && alertItems.some(it => String(it.id) === idStr)) {
            return;
        }

        const now = new Date();
        const pad = (n) => String(n).padStart(2, '0');
        const dateStr = `${pad(now.getDate())}/${pad(now.getMonth() + 1)}/${now.getFullYear()}`;
        const timeStr = `${pad(now.getHours())}:${pad(now.getMinutes())}`;
        const isDanger = data.severity === 'DANGER' || String(data.type || '').includes('DANGER') || String(data.alert_type || '').includes('DANGER') || data.type === 'SOS';

        alertItems.unshift({
            id: idStr,
            type: data.type || data.alert_type || 'ALERT',
            device: data.deviceUid || device,
            date: dateStr,
            time: timeStr,
            msg: data.message || 'Cảnh báo mới',
            severity: isDanger ? 'DANGER' : 'WARNING'
        });

        renderGroupedAlerts();
    }

    renderGroupedAlerts();

    // ── Còi tìm mũ & Tắt tiếng ──────────────────────────────────────────────
    function updateBuzzerUI(buzzerOn) {
        const subtext = document.getElementById('buzzer-subtext');
        const btn = document.getElementById('buzzer-btn');
        const inputOn = document.getElementById('buzzer-on-input');
        const mapBox = document.querySelector('.map-preview-box');

        if (subtext) subtext.textContent = buzzerOn ? 'Đang phát âm thanh' : 'Đang tắt';
        if (btn) {
            btn.textContent = buzzerOn ? 'Tắt tiếng' : 'Tìm mũ';
            btn.classList.toggle('is-beeping', buzzerOn);
        }
        if (inputOn) {
            inputOn.value = (!buzzerOn).toString();
        }
        if (mapBox) {
            mapBox.classList.toggle('finding-pulse', buzzerOn);
        }
    }

    const buzzerForm = document.getElementById('buzzer-form');
    if (buzzerForm) {
        buzzerForm.addEventListener('submit', async function (e) {
            e.preventDefault();
            const inputOn = document.getElementById('buzzer-on-input');
            const targetOn = inputOn ? inputOn.value === 'true' : true;
            const btn = document.getElementById('buzzer-btn');
            try {
                if (btn) btn.disabled = true;
                await fetch(buzzerForm.action, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: `on=${targetOn}`
                });
                updateBuzzerUI(targetOn);
            } catch (_) {
                buzzerForm.submit();
            } finally {
                if (btn) btn.disabled = false;
            }
        });
    }

    const initialGas = Number(airQualityNumber?.textContent);
    if (Number.isFinite(initialGas) && initialGas > 0) updateAirQuality({ gasPpm: initialGas });
    drawAirQuality();
    window.addEventListener('resize', drawAirQuality);

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
            const uid = data.device_uid ?? data.deviceUid ?? data.device_id;
            if (uid && uid !== device) return;

            // 1. Biểu đồ & nồng độ khí
            updateAirQuality(data);

            // 2. Trạng thái Online & thời gian cập nhật
            if (typeof data.is_online === 'boolean' || typeof data.isOnline === 'boolean' || ['ONLINE', 'OFFLINE'].includes(data.status)) {
                const online = data.is_online ?? data.isOnline ?? (data.status === 'ONLINE');
                updateOnlineStatus(online);
            }
            updateLastSeen();

            // 3. Tình trạng đội mũ
            const worn = data.is_worn ?? data.isWorn ?? data.worn;
            if (typeof worn === 'boolean' || data.worn_known === false) {
                const wornEl = document.getElementById('worn-stat');
                const subEl = document.getElementById('worn-subtext');
                if (wornEl) wornEl.textContent = data.worn_known === false ? 'Chưa xác định' : (worn ? 'Đang đội mũ' : 'Chưa đội mũ');
                if (subEl) subEl.textContent = data.worn_known === false ? 'Chưa bật cảm biến đội mũ' : (worn ? 'Công tắc đang bật' : 'Công tắc đang tắt');
            }

            // 4. Phát hiện té ngã
            const isFall = (data.type === 'FALL' || data.alert_type === 'FALL') && !['ACKNOWLEDGED', 'RESOLVED'].includes(data.status);
            if (isFall) {
                const now = new Date();
                const pad = (n) => String(n).padStart(2, '0');
                const timeStr = `${pad(now.getDate())}/${pad(now.getMonth() + 1)}/${now.getFullYear()} ${pad(now.getHours())}:${pad(now.getMinutes())}`;
                updateFallCard(true, timeStr);
            }

            // 5. Cập nhật vị trí GPS/Wi-Fi
            const lat = data.lastLat ?? data.latitude ?? data.lat ?? data.gps?.latitude;
            const lng = data.lastLng ?? data.longitude ?? data.lng ?? data.gps?.longitude;
            const source = data.location_source ?? data.source ?? data.gps?.source ?? (data.gps?.fix ? 'gps' : 'wifi');
            if (typeof lat === 'number' && typeof lng === 'number' && lat !== 0) {
                const coordsEl = document.getElementById('location-coords');
                if (coordsEl) coordsEl.textContent = `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
                const sourceEl = document.getElementById('location-source');
                if (sourceEl) {
                    sourceEl.textContent = (source === 'gps' || data.gps?.fix === true)
                        ? 'GPS ngoài trời · NEO-6M'
                        : 'Định vị Wi-Fi · Trong nhà';
                }
            }

            // 6. Trạng thái còi & Tìm mũ
            if (typeof data.buzzerOn === 'boolean' || typeof data.buzzer_on === 'boolean') {
                const bOn = data.buzzerOn ?? data.buzzer_on;
                updateBuzzerUI(bOn);
            }

            // 7. Xử lý cảnh báo (Alert Event / WebSocket)
            if (data.status === 'ACKNOWLEDGED' || data.status === 'RESOLVED') {
                if (data.id) {
                    removeAlertById(data.id);
                }
            } else if (data.message && (data.alert_type || data.type || data.severity)) {
                const isDanger = data.severity === 'DANGER' || String(data.type || '').includes('DANGER') || String(data.alert_type || '').includes('DANGER') || data.type === 'SOS';
                showToast(data.message, isDanger ? 'danger' : 'warning');
                prependAlert(data);
            }
        } catch (_) {
            // Ignore STOMP control frames and malformed messages.
        }
    });
}());
