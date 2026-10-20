/**
 * DashboardPage – trang chính hiển thị dữ liệu realtime từ Spring Boot và MySQL
 */
import { useTelemetryStream } from '../hooks/useTelemetryStream';
import { useAlerts } from '../hooks/useAlerts';
import { StatusBand } from '../components/StatusBand';
import { GasGauge } from '../components/GasGauge';
import { HelmetStatusCard } from '../components/HelmetStatusCard';
import { SiteMap } from '../components/SiteMap';
import { GasChart } from '../components/GasChart';
import { AlertList } from '../components/AlertList';
import { BuzzerButton } from '../components/BuzzerButton';

export function DashboardPage({ deviceState }) {
  const { samples, latest } = useTelemetryStream();
  const { alerts, acknowledge, newCount } = useAlerts();

  const state = deviceState || {
    id: '01',
    name: 'Mũ bảo hộ #01',
    zone: 'Khu vực hầm lò',
    isOnline: true,
    isWorn: true,
    buzzerOn: false,
    lastLat: 21.0285,
    lastLng: 105.8542,
  };

  const ppm = latest?.gas ?? state.gasPpm ?? 0;
  const warnThreshold   = 400;
  const dangerThreshold = 600;

  const topAlert = alerts.find(a => a.status === 'NEW' && a.severity === 'DANGER')
    || alerts.find(a => a.status === 'NEW');

  return (
    <div className="page animate-in">
      {/* Header */}
      <div className="page-header">
        <div>
          <h1 className="page-title">Dashboard</h1>
          <div className="page-sub">Giám sát mũ bảo hộ realtime · Mũ #{state.id || '01'}</div>
        </div>
      </div>

      {/* Stats row */}
      <div className="stats-row">
        <div className="stat-card">
          <div className="stat-icon">☣️</div>
          <div className="stat-value" style={{ color: ppm >= dangerThreshold ? 'var(--c-danger)' : ppm >= warnThreshold ? 'var(--c-warn)' : 'var(--c-safe)' }}>
            {Number(ppm).toFixed(0)}
          </div>
          <div className="stat-label">ppm hiện tại</div>
        </div>
        <div className="stat-card">
          <div className="stat-icon">🚨</div>
          <div className="stat-value" style={{ color: newCount > 0 ? 'var(--c-danger)' : 'var(--c-text)' }}>
            {newCount}
          </div>
          <div className="stat-label">Cảnh báo chưa xử lý</div>
        </div>
        <div className="stat-card">
          <div className="stat-icon">{state.isOnline ? '🟢' : '🔴'}</div>
          <div className="stat-value" style={{ fontSize: '1rem', color: state.isOnline ? 'var(--c-safe)' : 'var(--c-danger)' }}>
            {state.isOnline ? 'Online' : 'Offline'}
          </div>
          <div className="stat-label">Trạng thái kết nối</div>
        </div>
        <div className="stat-card">
          <div className="stat-icon">{state.isWorn ? '🪖' : '❌'}</div>
          <div className="stat-value" style={{ fontSize: '1rem', color: state.isWorn ? 'var(--c-safe)' : 'var(--c-warn)' }}>
            {state.isWorn ? 'Đang đội' : 'Không đội'}
          </div>
          <div className="stat-label">Tình trạng mũ</div>
        </div>
      </div>

      {/* Status band */}
      <StatusBand
        ppm={ppm}
        warnThreshold={warnThreshold}
        dangerThreshold={dangerThreshold}
        isOnline={state.isOnline}
        topAlert={topAlert}
        onAck={acknowledge}
      />

      {/* Main grid */}
      <div className="dashboard-grid" style={{ marginTop: 16 }}>
        {/* Left: main content */}
        <div className="dashboard-main">
          {/* Gauge + Map */}
          <div className="grid-2">
            <div className="card animate-in">
              <div className="card-title">☣️ Nồng độ khí MQ-135</div>
              <GasGauge
                ppm={ppm}
                warnThreshold={warnThreshold}
                dangerThreshold={dangerThreshold}
              />
            </div>

            <div className="card animate-in">
              <div className="card-title">🗺️ Vị trí mũ</div>
              <SiteMap
                lat={latest?.lat || state.lastLat || 21.0285}
                lng={latest?.lng || state.lastLng || 105.8542}
                gasPpm={ppm}
                warnThreshold={warnThreshold}
                dangerThreshold={dangerThreshold}
                isOnline={state.isOnline}
              />
            </div>
          </div>

          {/* Gas chart */}
          <GasChart
            samples={samples}
            warnThreshold={warnThreshold}
            dangerThreshold={dangerThreshold}
            title="Biểu đồ nồng độ khí live từ cảm biến"
            height={220}
          />

          {/* Alert list */}
          <div className="card animate-in">
            <div className="card-title">
              🚨 Cảnh báo gần đây
              {newCount > 0 && (
                <span className="badge badge-danger" style={{ marginLeft: 8 }}>{newCount} mới</span>
              )}
            </div>
            <AlertList
              alerts={alerts}
              onAck={acknowledge}
              maxItems={5}
            />
          </div>
        </div>

        {/* Right: sidebar */}
        <div className="dashboard-side">
          <HelmetStatusCard state={state} />
          <div className="card animate-in">
            <BuzzerButton
              deviceId={state.id || '01'}
              buzzerOn={state.buzzerOn}
            />
          </div>

          {/* Fall status */}
          <div className="card animate-in" style={{
            background: latest?.fall ? 'var(--c-danger-dim)' : 'var(--c-surface)',
            borderColor: latest?.fall ? 'rgba(248,81,73,0.4)' : 'var(--c-border)',
          }}>
            <div className="card-title">🚨 Phát hiện té ngã</div>
            <div style={{
              textAlign: 'center',
              padding: '16px 0',
              fontSize: latest?.fall ? '3rem' : '2rem',
              filter: latest?.fall ? 'drop-shadow(0 0 12px rgba(248,81,73,0.6))' : 'none',
            }}>
              {latest?.fall ? '🚨' : '✅'}
            </div>
            <div style={{
              textAlign: 'center',
              fontWeight: 600,
              color: latest?.fall ? 'var(--c-danger)' : 'var(--c-safe)',
              fontSize: '0.9rem',
            }}>
              {latest?.fall ? 'TÉ NGÃ!' : 'Bình thường'}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
