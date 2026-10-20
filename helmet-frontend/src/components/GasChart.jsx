/**
 * GasChart – biểu đồ đường nồng độ khí (Recharts)
 * Dùng cho Dashboard (60 giây live) và HistoryPage
 */
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
  ReferenceLine, ResponsiveContainer, Area, AreaChart, Legend,
} from 'recharts';
import { formatTime } from '../utils/format';

const CustomTooltip = ({ active, payload, label, warnThreshold, dangerThreshold }) => {
  if (!active || !payload?.length) return null;
  const ppm = payload[0]?.value;
  const color =
    ppm >= dangerThreshold ? 'var(--c-danger)' :
    ppm >= warnThreshold   ? 'var(--c-warn)'   : 'var(--c-safe)';
  return (
    <div style={{
      background: 'var(--c-surface)',
      border: '1px solid var(--c-border)',
      borderRadius: 8, padding: '10px 14px',
      fontSize: '0.82rem', fontFamily: 'var(--font-mono)',
    }}>
      <div style={{ color: 'var(--c-text-muted)', marginBottom: 4 }}>{label}</div>
      <div style={{ color, fontWeight: 700, fontSize: '1.1rem' }}>{ppm?.toFixed(1)} ppm</div>
    </div>
  );
};

export function GasChart({ samples = [], warnThreshold = 400, dangerThreshold = 600, height = 220, title }) {
  const data = samples.map(s => ({
    time: formatTime(s.ts || s.recordedAt),
    gas: typeof s.gas === 'number' ? s.gas : s.gasPpm,
  }));

  // Màu stroke theo giá trị tối đa hiện tại
  const maxPpm = data.length ? Math.max(...data.map(d => d.gas || 0)) : 0;
  const strokeColor =
    maxPpm >= dangerThreshold ? '#f85149' :
    maxPpm >= warnThreshold   ? '#d29922' : '#3fb950';

  return (
    <div className="card animate-in">
      {title && (
        <div className="card-title">
          📈 {title}
        </div>
      )}
      {data.length === 0 ? (
        <div className="empty-state" style={{ padding: '40px' }}>
          <div className="empty-state-icon">📉</div>
          <div className="empty-state-text">Chưa có dữ liệu</div>
        </div>
      ) : (
        <ResponsiveContainer width="100%" height={height}>
          <AreaChart data={data} margin={{ top: 8, right: 4, left: -10, bottom: 0 }}>
            <defs>
              <linearGradient id="gasGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%"  stopColor={strokeColor} stopOpacity={0.25} />
                <stop offset="95%" stopColor={strokeColor} stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid
              strokeDasharray="3 3"
              stroke="var(--c-border-2)"
              vertical={false}
            />
            <XAxis
              dataKey="time"
              tick={{ fontSize: 10, fill: 'var(--c-text-muted)', fontFamily: 'var(--font-mono)' }}
              tickLine={false}
              axisLine={{ stroke: 'var(--c-border)' }}
              interval="preserveStartEnd"
            />
            <YAxis
              tick={{ fontSize: 10, fill: 'var(--c-text-muted)', fontFamily: 'var(--font-mono)' }}
              tickLine={false}
              axisLine={false}
              domain={[0, Math.max(dangerThreshold * 1.3, maxPpm * 1.1, 100)]}
            />
            <Tooltip
              content={<CustomTooltip warnThreshold={warnThreshold} dangerThreshold={dangerThreshold} />}
              cursor={{ stroke: 'var(--c-border)', strokeWidth: 1 }}
            />
            <ReferenceLine
              y={warnThreshold}
              stroke="var(--c-warn)"
              strokeDasharray="5 4"
              strokeWidth={1.5}
              label={{
                value: `Cảnh báo ${warnThreshold}`,
                fill: 'var(--c-warn)',
                fontSize: 10,
                position: 'insideTopRight',
              }}
            />
            <ReferenceLine
              y={dangerThreshold}
              stroke="var(--c-danger)"
              strokeDasharray="5 4"
              strokeWidth={1.5}
              label={{
                value: `Nguy hiểm ${dangerThreshold}`,
                fill: 'var(--c-danger)',
                fontSize: 10,
                position: 'insideTopRight',
              }}
            />
            <Area
              type="monotone"
              dataKey="gas"
              stroke={strokeColor}
              strokeWidth={2.5}
              fill="url(#gasGrad)"
              dot={false}
              activeDot={{ r: 5, fill: strokeColor, stroke: 'var(--c-bg)', strokeWidth: 2 }}
              isAnimationActive={false}
            />
          </AreaChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}
