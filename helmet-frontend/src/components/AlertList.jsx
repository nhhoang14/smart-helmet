/**
 * AlertList – danh sách cảnh báo với nút xác nhận
 */
import { formatDateTime, alertTypeLabel, severityClass } from '../utils/format';

const TYPE_ICONS = {
  GAS_WARNING: '⚠️',
  GAS_DANGER:  '☣️',
  FALL:        '🚨',
  SOS:         '🆘',
  OFFLINE:     '📴',
};

export function AlertList({ alerts = [], onAck, maxItems = 8, showAll = false }) {
  const displayed = showAll ? alerts : alerts.slice(0, maxItems);

  if (!displayed.length) return (
    <div className="empty-state">
      <div className="empty-state-icon">🛡️</div>
      <div className="empty-state-text">Không có cảnh báo nào</div>
    </div>
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {displayed.map((alert, i) => (
        <div
          key={alert.id}
          className={`alert-item ${alert.status === 'ACKNOWLEDGED' ? 'acked' : ''}`}
          style={{ animationDelay: `${i * 40}ms` }}
        >
          <div className={`alert-icon ${severityClass(alert.severity)}`}>
            {TYPE_ICONS[alert.type] || '❗'}
          </div>

          <div className="alert-body">
            <div className="alert-msg">{alert.message}</div>
            <div className="alert-meta">
              <span style={{ color: alert.severity === 'DANGER' ? 'var(--c-danger)' : 'var(--c-warn)' }}>
                {alertTypeLabel(alert.type)}
              </span>
              {' · '}
              {formatDateTime(alert.createdAt)}
              {alert.gasPpm && ` · ${alert.gasPpm} ppm`}
              {alert.lat && ` · ${alert.lat.toFixed(4)}°N`}
            </div>
            {alert.status === 'ACKNOWLEDGED' && alert.acknowledgedAt && (
              <div style={{ fontSize: '0.72rem', color: 'var(--c-safe)', marginTop: 3 }}>
                ✓ Đã xử lý lúc {formatDateTime(alert.acknowledgedAt)}
              </div>
            )}
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 6 }}>
            <span className={`badge ${alert.severity === 'DANGER' ? 'badge-danger' : 'badge-warn'}`}>
              {alert.severity}
            </span>
            {alert.status === 'NEW' && onAck && (
              <button
                className="btn btn-ghost btn-sm"
                onClick={() => onAck(alert.id)}
                title="Xác nhận đã xử lý"
              >
                ✓ Xác nhận
              </button>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
