(function () {
  const device = document.body.dataset.device;
  if (!device || !window.WebSocket) return;
  const airQualityValues = [];
  const chart = document.getElementById('air-quality-chart');
  const chartEmpty = document.getElementById('air-quality-empty');
  const airQualityValue = document.getElementById('air-quality-value');

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
    if (!airQualityValues.length) return;

    const min = Math.max(0, Math.min(...airQualityValues) - 30);
    const max = Math.max(min + 100, Math.max(...airQualityValues) + 30);
    const step = width / Math.max(airQualityValues.length - 1, 1);
    context.beginPath();
    airQualityValues.forEach((value, index) => {
      const x = index * step;
      const y = height - ((value - min) / (max - min)) * (height - 12) - 6;
      index ? context.lineTo(x, y) : context.moveTo(x, y);
    });
    context.strokeStyle = '#e08a2e';
    context.lineWidth = 2.5;
    context.stroke();
  }

  function updateAirQuality(data) {
    const sensors = data.sensors || {};
    const raw = data.gasPpm ?? data.gas ?? sensors.gas_ppm ?? sensors.gas;
    const value = Number(raw);
    if (!Number.isFinite(value) || value <= 0) return;
    airQualityValues.push(value);
    if (airQualityValues.length > 30) airQualityValues.shift();
    if (airQualityValue) airQualityValue.innerHTML = `${Math.round(value)} <small>PPM</small>`;
    if (chartEmpty) chartEmpty.hidden = true;
    drawAirQuality();
  }

  const initialGas = Number(document.getElementById('gas-value')?.textContent);
  if (Number.isFinite(initialGas) && initialGas > 0) updateAirQuality({ gasPpm: initialGas });
  window.addEventListener('resize', drawAirQuality);
  const socket = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`);
  socket.addEventListener('open', function () {
    socket.send('CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\0');
    socket.send(`SUBSCRIBE\nid:helmet-dashboard\ndestination:/topic/device/${device}\n\n\0`);
  });
  socket.addEventListener('message', function (event) {
    const body = event.data.split('\n\n')[1];
    if (!body) return;
    try {
      const data = JSON.parse(body.replace(/\0$/, ''));
      updateAirQuality(data);
      if (typeof data.gasPpm === 'number' || typeof data.gas === 'number') {
        const gas = Number(data.gasPpm ?? data.gas);
        document.querySelectorAll('#gas-value,#gas-stat').forEach((el) => { el.textContent = Math.round(gas); });
        const gauge = document.getElementById('gas-gauge');
        if (gauge) gauge.style.setProperty('--gauge', `${Math.min(gas / 6, 100)}%`);
      }
      if (typeof data.isOnline === 'boolean' || typeof data.status === 'string') {
        const online = data.isOnline ?? data.status === 'ONLINE';
        const el = document.getElementById('online-chip');
        if (el) {
          el.textContent = online ? 'ONLINE' : 'OFFLINE';
          el.classList.toggle('offline', !online);
        }
      }
      if (typeof data.isWorn === 'boolean') {
        const el = document.getElementById('worn-stat');
        if (el) el.textContent = data.isWorn ? 'Đang đội' : 'Không đội';
      }
      if (typeof data.buzzerOn === 'boolean') {
        const el = document.getElementById('buzzer-state');
        if (el) el.textContent = data.buzzerOn ? 'Đang bật' : 'Đang tắt';
      }
    } catch (_) {
      // Ignore STOMP control frames and malformed messages.
    }
  });
}());
