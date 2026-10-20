/**
 * REST API client
 * Tất cả URL gốc tập trung tại đây
 */

const BASE = import.meta.env.VITE_API_BASE || '';

async function request(method, path, body) {
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json' },
  };
  if (body !== undefined) opts.body = JSON.stringify(body);
  const res = await fetch(`${BASE}${path}`, opts);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${method} ${path} → ${res.status}: ${text}`);
  }
  const ct = res.headers.get('content-type') || '';
  if (ct.includes('application/json')) return res.json();
  return null;
}

// --- Devices ---
export const getDevices = ()              => request('GET', '/api/devices');
export const getDevice  = (id)            => request('GET', `/api/devices/${id}`);

// --- Telemetry ---
export const getTelemetry = (id, from, to) =>
  request('GET', `/api/devices/${id}/telemetry?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);

// --- Alerts ---
export const getAlerts  = (params = {}) => {
  const q = new URLSearchParams(params).toString();
  return request('GET', `/api/alerts${q ? '?' + q : ''}`);
};
export const ackAlert   = (id) => request('POST', `/api/alerts/${id}/ack`);

// --- Commands ---
export const setBuzzer  = (id, on) => request('POST', `/api/devices/${id}/buzzer`, { on });

// --- Settings ---
export const getSettings    = (id)      => request('GET',  `/api/devices/${id}/settings`);
export const updateSettings = (id, dto) => request('PUT',  `/api/devices/${id}/settings`, dto);
