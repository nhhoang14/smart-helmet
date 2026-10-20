/**
 * StatusBand – dải trạng thái lớn hiển thị mức an toàn / cảnh báo / nguy hiểm
 */
import { gasLevel } from '../utils/format';

const ICONS = {
  safe:    '✅',
  warning: '⚠️',
  danger:  '🚨',
  offline: '📴',
};

const LABELS = {
  safe:    'AN TOÀN',
  warning: 'CẢNH BÁO',
  danger:  'NGUY HIỂM',
  offline: 'MẤT KẾT NỐI',
};

export function StatusBand({ ppm, warnThreshold = 400, dangerThreshold = 600, isOnline = true, topAlert, onAck }) {
  let level;
  if (!isOnline) {
    level = 'offline';
  } else if (ppm >= dangerThreshold) {
    level = 'danger';
  } else if (ppm >= warnThreshold) {
    level = 'warning';
  } else {
    level = 'safe';
  }

  return (
    <div className={`status-band ${level}`}>
      <div className="status-band-left">
        <div className="status-band-icon">{ICONS[level]}</div>
        <div>
          <div className="status-band-label">{LABELS[level]}</div>
          <div className="status-band-sub">
            {level === 'offline'
              ? 'Thiết bị không phản hồi'
              : `Nồng độ khí: ${ppm != null ? ppm.toFixed(1) : '---'} ppm`}
          </div>
          {topAlert && (
            <div style={{ marginTop: 6, fontSize: '0.82rem', color: 'var(--c-text-muted)' }}>
              {topAlert.message}
            </div>
          )}
        </div>
      </div>

      {topAlert && topAlert.status === 'NEW' && onAck && (
        <button
          className="btn btn-ghost btn-sm"
          onClick={() => onAck(topAlert.id)}
          title="Xác nhận đã xử lý"
        >
          ✓ Đã xử lý
        </button>
      )}
    </div>
  );
}
