/**
 * HelmetStatusCard – thẻ trạng thái thiết bị
 */
import { formatRelative, formatCoord } from '../utils/format';

function Row({ icon, label, value, valueClass }) {
  return (
    <div className="status-row">
      <span className="status-row-label">
        <span>{icon}</span> {label}
      </span>
      <span className={`status-row-value ${valueClass || ''}`} style={{ color: valueClass ? undefined : 'var(--c-text)' }}>
        {value}
      </span>
    </div>
  );
}

export function HelmetStatusCard({ state }) {
  if (!state) return (
    <div className="card">
      <div className="card-title">⛑️ Trạng thái mũ</div>
      <div style={{ display: 'flex', justifyContent: 'center', padding: '24px' }}>
        <div className="spinner" />
      </div>
    </div>
  );

  const { isOnline, isWorn, buzzerOn, lastSeenAt, lastLat, lastLng, name, zone } = state;

  return (
    <div className="card animate-in">
      <div className="card-title">
        ⛑️ {name || 'Mũ bảo hộ #01'}
        <span className="badge badge-info" style={{ marginLeft: 'auto', fontFamily: 'var(--font-mono)', fontSize: '0.7rem' }}>
          ID: {state.id || '01'}
        </span>
      </div>
      {zone && (
        <div style={{ fontSize: '0.78rem', color: 'var(--c-text-muted)', marginBottom: 12 }}>
          📍 Khu vực: {zone}
        </div>
      )}
      <Row
        icon="🔗"
        label="Kết nối"
        value={isOnline ? 'Online' : 'Offline'}
        valueClass=""
      />
      <div className="status-row">
        <span className="status-row-label"><span>🔗</span> Kết nối</span>
        <span>
          <span className={`badge ${isOnline ? 'badge-safe' : 'badge-offline'}`}>
            <span className={`dot ${isOnline ? 'dot-safe dot-pulse' : 'dot-offline'}`} />
            {isOnline ? 'Online' : 'Offline'}
          </span>
        </span>
      </div>
      <div className="status-row">
        <span className="status-row-label"><span>🪖</span> Đang đội</span>
        <span>
          <span className={`badge ${isWorn ? 'badge-safe' : 'badge-warn'}`}>
            {isWorn ? '✓ Có' : '✗ Không'}
          </span>
        </span>
      </div>
      <div className="status-row">
        <span className="status-row-label"><span>🔔</span> Còi</span>
        <span>
          <span className={`badge ${buzzerOn ? 'badge-warn' : 'badge-offline'}`}>
            {buzzerOn ? '🔔 Đang bật' : '🔕 Tắt'}
          </span>
        </span>
      </div>
      <div className="status-row">
        <span className="status-row-label"><span>🗺️</span> Vị trí</span>
        <span className="status-row-value" style={{ fontSize: '0.78rem' }}>
          {formatCoord(lastLat, lastLng)}
        </span>
      </div>
      <div className="status-row">
        <span className="status-row-label"><span>🕐</span> Cập nhật</span>
        <span className="status-row-value" style={{ color: 'var(--c-text-muted)' }}>
          {formatRelative(lastSeenAt)}
        </span>
      </div>
    </div>
  );
}
