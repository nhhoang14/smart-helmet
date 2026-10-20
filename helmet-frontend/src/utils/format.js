// Các hàm định dạng hiển thị

/**
 * Định dạng giá trị ppm
 */
export function formatPpm(value) {
  if (value === null || value === undefined) return '---';
  return `${Number(value).toFixed(1)} ppm`;
}

/**
 * Định dạng timestamp thành giờ:phút:giây
 */
export function formatTime(ts) {
  if (!ts) return '---';
  const d = typeof ts === 'number' ? new Date(ts * 1000) : new Date(ts);
  return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

/**
 * Định dạng đầy đủ ngày giờ
 */
export function formatDateTime(ts) {
  if (!ts) return '---';
  const d = typeof ts === 'number' ? new Date(ts * 1000) : new Date(ts);
  return d.toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
}

/**
 * Thời gian tương đối ("2 phút trước")
 */
export function formatRelative(ts) {
  if (!ts) return '---';
  const d = typeof ts === 'number' ? new Date(ts * 1000) : new Date(ts);
  const diffMs = Date.now() - d.getTime();
  const secs = Math.floor(diffMs / 1000);
  if (secs < 5)  return 'vừa xong';
  if (secs < 60) return `${secs} giây trước`;
  const mins = Math.floor(secs / 60);
  if (mins < 60) return `${mins} phút trước`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24)  return `${hrs} giờ trước`;
  return formatDateTime(ts);
}

/**
 * Định dạng tọa độ GPS
 */
export function formatCoord(lat, lng) {
  if (lat == null || lng == null) return '---';
  return `${lat.toFixed(5)}°N, ${lng.toFixed(5)}°E`;
}

/**
 * Lấy màu theo mức ppm
 */
export function gasColor(ppm, warnThreshold = 400, dangerThreshold = 600) {
  if (ppm >= dangerThreshold) return 'var(--c-danger)';
  if (ppm >= warnThreshold)   return 'var(--c-warn)';
  return 'var(--c-safe)';
}

/**
 * Lấy tên mức cảnh báo
 */
export function gasLevel(ppm, warnThreshold = 400, dangerThreshold = 600) {
  if (ppm >= dangerThreshold) return 'NGUY HIỂM';
  if (ppm >= warnThreshold)   return 'CẢNH BÁO';
  return 'AN TOÀN';
}

/**
 * Map loại cảnh báo sang tiếng Việt
 */
export function alertTypeLabel(type) {
  const map = {
    GAS_WARNING: '⚠️ Cảnh báo khí',
    GAS_DANGER:  '☣️ Nguy hiểm khí',
    FALL:        '🚨 Té ngã',
    SOS:         '🆘 SOS',
    OFFLINE:     '📴 Mất kết nối',
  };
  return map[type] || type;
}

/**
 * Map severity sang class CSS
 */
export function severityClass(severity) {
  return severity === 'DANGER' ? 'danger' : 'warning';
}

/**
 * Format cho input datetime-local
 */
export function toDatetimeLocal(date) {
  const d = new Date(date);
  const offset = d.getTimezoneOffset();
  const local = new Date(d.getTime() - offset * 60 * 1000);
  return local.toISOString().slice(0, 16);
}
